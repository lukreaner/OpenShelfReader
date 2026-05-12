package org.openshelf.reader.kavita.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.test.runTest
import org.openshelf.reader.core.PublicationFormat
import org.openshelf.reader.source.api.DownloadResult
import org.openshelf.reader.source.api.RemoteBookId
import org.openshelf.reader.source.api.RemoteDownloadRequest
import org.openshelf.reader.source.api.RemoteFileId
import org.openshelf.reader.source.api.SourceError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KavitaDownloadClientTest {
    @Test
    fun downloadUsesChapterRouteAndApiKeyHeader() = runTest {
        val capturedRequests = mutableListOf<HttpRequestData>()
        val client = downloadClient(
            baseUrl = KavitaBaseUrl.normalize("https://library.example/kavita"),
            capturedRequests = capturedRequests,
        )
        val chunks = mutableListOf<Byte>()

        val result = client.downloadFile(downloadRequest()) { chunk ->
            chunks += chunk.toList()
        }

        val request = capturedRequests.single()
        assertEquals("/kavita/api/Download/chapter", request.url.encodedPath)
        assertEquals("301", request.url.parameters["chapterId"])
        assertEquals(TestApiKey, request.headers[KavitaAuthenticationClient.ApiKeyHeader])

        val success = assertIs<DownloadResult.Success>(result)
        assertEquals(RemoteFileId("401"), success.fileId)
        assertEquals(PublicationFormat.EPUB, success.format)
        assertEquals(5L, success.bytesWritten)
        assertEquals("book.epub", success.fileName)
        assertEquals("application/epub+zip", success.contentType)
        assertEquals(listOf(1, 2, 3, 4, 5).map(Int::toByte), chunks)
    }

    @Test
    fun unauthorizedAndForbiddenMapToUnauthorized() = runTest {
        listOf(HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden).forEach { status ->
            val result = downloadClient(status = status).downloadFile(downloadRequest()) { _ -> }

            val failure = assertIs<DownloadResult.Failure>(result)
            assertEquals(SourceError.Unauthorized, failure.error)
        }
    }

    @Test
    fun notFoundAndNoContentMapToNotFound() = runTest {
        listOf(HttpStatusCode.NotFound, HttpStatusCode.NoContent).forEach { status ->
            val result = downloadClient(status = status).downloadFile(downloadRequest()) { _ -> }

            val failure = assertIs<DownloadResult.Failure>(result)
            assertEquals(SourceError.NotFound, failure.error)
        }
    }

    @Test
    fun networkFailuresMapToNetworkUnavailable() = runTest {
        val httpClient = HttpClient(
            MockEngine {
                throw IOException("Network unavailable")
            },
        )
        val client = KavitaDownloadClient(
            httpClient = httpClient,
            baseUrl = KavitaBaseUrl.normalize("https://library.example"),
            apiKey = TestApiKey,
        )

        val result = client.downloadFile(downloadRequest()) { _ -> }

        val failure = assertIs<DownloadResult.Failure>(result)
        assertEquals(SourceError.NetworkUnavailable, failure.error)
    }

    @Test
    fun unexpectedResponsesRedactApiKey() = runTest {
        val result = downloadClient(
            status = HttpStatusCode.InternalServerError,
            responseBody = "server echoed $TestApiKey",
        ).downloadFile(downloadRequest()) { _ -> }

        val failure = assertIs<DownloadResult.Failure>(result)
        val error = assertIs<SourceError.UnexpectedResponse>(failure.error)
        assertEquals(500, error.status)
        assertFalse(error.toString().contains(TestApiKey))
        assertTrue(error.bodySnippet?.contains(KavitaRedactedSecret) == true)
    }

    @Test
    fun apiKeyDoesNotLeakFromStringOutput() {
        val client = downloadClient()

        assertFalse(client.toString().contains(TestApiKey))
        assertTrue(client.toString().contains(KavitaRedactedSecret))
    }

    private fun downloadClient(
        baseUrl: KavitaBaseUrl = KavitaBaseUrl.normalize("https://library.example"),
        status: HttpStatusCode = HttpStatusCode.OK,
        responseBody: Any = byteArrayOf(1, 2, 3, 4, 5),
        capturedRequests: MutableList<HttpRequestData> = mutableListOf(),
    ): KavitaDownloadClient {
        return downloadClient(baseUrl = baseUrl, capturedRequests = capturedRequests) { request ->
            capturedRequests += request
            when (responseBody) {
                is ByteArray -> respond(
                    content = responseBody,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/epub+zip"),
                )

                is String -> respond(
                    content = responseBody,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/epub+zip"),
                )

                else -> error("Unsupported response body.")
            }
        }
    }

    private fun downloadClient(
        baseUrl: KavitaBaseUrl = KavitaBaseUrl.normalize("https://library.example"),
        capturedRequests: MutableList<HttpRequestData> = mutableListOf(),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): KavitaDownloadClient {
        return KavitaDownloadClient(
            httpClient = HttpClient(MockEngine(handler)),
            baseUrl = baseUrl,
            apiKey = TestApiKey,
            bufferSize = 2,
        )
    }

    private fun downloadRequest(): RemoteDownloadRequest =
        RemoteDownloadRequest(
            bookId = RemoteBookId("301"),
            fileId = RemoteFileId("401"),
            expectedFormat = PublicationFormat.EPUB,
            expectedFileName = "book.epub",
            expectedSizeBytes = 5L,
        )

    private companion object {
        const val TestApiKey = "kavita-test-api-key"
    }
}
