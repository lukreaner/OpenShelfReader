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
import org.openshelf.reader.source.api.RemoteBookId
import org.openshelf.reader.source.api.RemoteLibraryId
import org.openshelf.reader.source.api.RemoteSeriesId
import org.openshelf.reader.source.api.SourceAdapterException
import org.openshelf.reader.source.api.SourceError
import org.openshelf.reader.source.api.SourceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KavitaBrowsingClientTest {
    @Test
    fun listLibrariesSendsApiKeyHeader() = runTest {
        val capturedRequests = mutableListOf<HttpRequestData>()
        val client = browsingClient(capturedRequests = capturedRequests)

        client.listLibraries()

        val request = capturedRequests.single()
        assertEquals(TestApiKey, request.headers[KavitaAuthenticationClient.ApiKeyHeader])
    }

    @Test
    fun listLibrariesMapsExpectedFields() = runTest {
        val client = browsingClient(
            responseBody = """
                [
                  {"id": 7, "name": "Fiction"},
                  {"id": 8, "name": "Reference"}
                ]
            """.trimIndent(),
        )

        val libraries = client.listLibraries()

        assertEquals(2, libraries.size)
        assertEquals(RemoteLibraryId("7"), libraries[0].id)
        assertEquals(SourceId("kavita-source"), libraries[0].sourceId)
        assertEquals("Fiction", libraries[0].name)
        assertEquals(RemoteLibraryId("8"), libraries[1].id)
        assertEquals("Reference", libraries[1].name)
    }

    @Test
    fun browsingEndpointsPreserveBaseUrlPathPrefix() = runTest {
        val capturedRequests = mutableListOf<HttpRequestData>()
        val client = browsingClient(
            baseUrl = KavitaBaseUrl.normalize("https://library.example/kavita/"),
            capturedRequests = capturedRequests,
        )

        client.listLibraries()

        assertEquals("/kavita/api/Library/libraries", capturedRequests.single().url.encodedPath)
    }

    @Test
    fun unauthorizedAndForbiddenMapToUnauthorized() = runTest {
        listOf(HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden).forEach { status ->
            val client = browsingClient(status = status)

            val exception = assertFailsWith<SourceAdapterException> {
                client.listLibraries()
            }

            assertEquals(SourceError.Unauthorized, exception.error)
        }
    }

    @Test
    fun networkFailuresMapToNetworkUnavailable() = runTest {
        val httpClient = HttpClient(
            MockEngine {
                throw IOException("Network unavailable")
            },
        )
        val client = KavitaBrowsingClient(
            httpClient = httpClient,
            sourceId = SourceId("kavita-source"),
            baseUrl = KavitaBaseUrl.normalize("https://library.example"),
            apiKey = TestApiKey,
        )

        val exception = assertFailsWith<SourceAdapterException> {
            client.listLibraries()
        }

        assertEquals(SourceError.NetworkUnavailable, exception.error)
    }

    @Test
    fun unexpectedJsonMapsToUnexpectedResponse() = runTest {
        val client = browsingClient(responseBody = "not valid json")

        val exception = assertFailsWith<SourceAdapterException> {
            client.listLibraries()
        }

        val error = assertIs<SourceError.UnexpectedResponse>(exception.error)
        assertEquals(200, error.status)
        assertTrue(error.bodySnippet?.contains("not valid json") == true)
    }

    @Test
    fun apiKeyDoesNotLeakThroughErrorValuesOrStringOutput() = runTest {
        val client = browsingClient(
            status = HttpStatusCode.InternalServerError,
            responseBody = "server echoed $TestApiKey",
        )

        val exception = assertFailsWith<SourceAdapterException> {
            client.listLibraries()
        }

        assertFalse(client.toString().contains(TestApiKey))
        assertFalse(exception.toString().contains(TestApiKey))
        assertFalse(exception.error.toString().contains(TestApiKey))
        val error = assertIs<SourceError.UnexpectedResponse>(exception.error)
        assertTrue(error.bodySnippet?.contains(KavitaRedactedSecret) == true)
    }

    @Test
    fun seriesAndBookBrowsingMapRepresentativeKavitaResponses() = runTest {
        val capturedRequests = mutableListOf<HttpRequestData>()
        val client = browsingClient(
            baseUrl = KavitaBaseUrl.normalize("https://library.example/kavita"),
            capturedRequests = capturedRequests,
        ) { request ->
            capturedRequests += request
            when (request.url.encodedPath) {
                "/kavita/api/Series/all-v2" -> jsonResponse(
                    """
                        [
                          {"id": 101, "localizedName": "The Expanse", "name": "Expanse", "libraryId": 7}
                        ]
                    """.trimIndent(),
                )

                "/kavita/api/Series/volumes" -> jsonResponse(
                    """
                        [
                          {
                            "id": 201,
                            "seriesId": 101,
                            "chapters": [
                              {
                                "id": 301,
                                "titleName": "Leviathan Wakes",
                                "writers": [{"id": 1, "name": "James S. A. Corey"}],
                                "files": [
                                  {
                                    "id": 401,
                                    "filePath": "Books/Leviathan Wakes.epub",
                                    "bytes": 123456,
                                    "format": 3,
                                    "extension": "epub"
                                  }
                                ]
                              },
                              {
                                "id": 302,
                                "title": "Caliban's War",
                                "files": [
                                  {
                                    "id": 402,
                                    "filePath": "Books/Caliban's War.pdf",
                                    "bytes": 234567,
                                    "format": 4,
                                    "extension": "pdf"
                                  }
                                ]
                              }
                            ]
                          }
                        ]
                    """.trimIndent(),
                )

                "/kavita/api/Series/chapter" -> jsonResponse(
                    """
                        {
                          "id": 301,
                          "titleName": "Leviathan Wakes",
                          "summary": "A representative sanitized summary.",
                          "releaseDate": "2011-06-02T00:00:00Z",
                          "writers": [{"id": 1, "name": "James S. A. Corey"}],
                          "files": [
                            {
                              "id": 401,
                              "filePath": "Books/Leviathan Wakes.epub",
                              "bytes": 123456,
                              "format": 3,
                              "extension": "epub"
                            }
                          ]
                        }
                    """.trimIndent(),
                )

                "/kavita/api/Book/301/book-info" -> jsonResponse(
                    """
                        {
                          "bookTitle": "Leviathan Wakes",
                          "seriesId": 101,
                          "volumeId": 201,
                          "seriesFormat": 3,
                          "seriesName": "The Expanse",
                          "libraryId": 7,
                          "pages": 561
                        }
                    """.trimIndent(),
                )

                else -> error("Unexpected route: ${request.url}")
            }
        }

        val series = client.listSeries(RemoteLibraryId("7"))
        assertEquals(RemoteSeriesId("101"), series.single().id)
        assertEquals(RemoteLibraryId("7"), series.single().libraryId)
        assertEquals("The Expanse", series.single().title)

        val books = client.listBooks(RemoteSeriesId("101"))
        assertEquals(2, books.size)
        assertEquals(RemoteBookId("301"), books[0].id)
        assertEquals("Leviathan Wakes", books[0].title)
        assertEquals(listOf("James S. A. Corey"), books[0].authors)
        assertEquals(setOf(PublicationFormat.EPUB), books[0].formats)
        assertEquals(RemoteBookId("302"), books[1].id)
        assertEquals("Caliban's War", books[1].title)
        assertEquals(setOf(PublicationFormat.PDF), books[1].formats)

        val details = client.getBook(RemoteBookId("301"))
        assertEquals(RemoteBookId("301"), details.id)
        assertEquals("Leviathan Wakes", details.title)
        assertEquals(listOf("James S. A. Corey"), details.authors)
        assertEquals(RemoteLibraryId("7"), details.libraryId)
        assertEquals(RemoteSeriesId("101"), details.seriesId)
        assertEquals(setOf(PublicationFormat.EPUB), details.formats)
        assertEquals("A representative sanitized summary.", details.summary)
        assertEquals(2011, details.publishedYear)

        val file = assertNotNull(details.files.singleOrNull())
        assertEquals("401", file.id.value)
        assertEquals(PublicationFormat.EPUB, file.format)
        assertEquals("Leviathan Wakes.epub", file.fileName)
        assertEquals(123456L, file.sizeBytes)

        assertTrue(capturedRequests.any { it.url.encodedPath == "/kavita/api/Series/all-v2" })
        assertTrue(capturedRequests.any { it.url.parameters["PageNumber"] == "1" })
        assertTrue(capturedRequests.all { it.headers[KavitaAuthenticationClient.ApiKeyHeader] == TestApiKey })
    }

    private fun browsingClient(
        baseUrl: KavitaBaseUrl = KavitaBaseUrl.normalize("https://library.example"),
        status: HttpStatusCode = HttpStatusCode.OK,
        responseBody: String = """[{"id": 7, "name": "Fiction"}]""",
        capturedRequests: MutableList<HttpRequestData> = mutableListOf(),
    ): KavitaBrowsingClient {
        return browsingClient(
            baseUrl = baseUrl,
            capturedRequests = capturedRequests,
        ) { request ->
            capturedRequests += request
            jsonResponse(responseBody, status)
        }
    }

    private fun browsingClient(
        baseUrl: KavitaBaseUrl = KavitaBaseUrl.normalize("https://library.example"),
        capturedRequests: MutableList<HttpRequestData> = mutableListOf(),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): KavitaBrowsingClient {
        val httpClient = HttpClient(MockEngine(handler))
        return KavitaBrowsingClient(
            httpClient = httpClient,
            sourceId = SourceId("kavita-source"),
            baseUrl = baseUrl,
            apiKey = TestApiKey,
        )
    }

    private fun MockRequestHandleScope.jsonResponse(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpResponseData {
        return respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

    private companion object {
        const val TestApiKey = "kavita-test-api-key"
    }
}
