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
import org.openshelf.reader.source.api.SourceCredentials
import org.openshelf.reader.source.api.SourceError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KavitaAuthenticationClientTest {
    @Test
    fun baseUrlNormalizationTrimsWhitespaceAndTrailingSlashes() {
        val normalized = KavitaBaseUrl.normalize("  https://library.example/kavita///  ")

        assertEquals("https://library.example/kavita", normalized.value)
    }

    @Test
    fun baseUrlNormalizationRejectsBlankOrInvalidValues() {
        assertFailsWith<IllegalArgumentException> {
            KavitaBaseUrl.normalize(" ")
        }
        assertFailsWith<IllegalArgumentException> {
            KavitaBaseUrl.normalize("library.example")
        }
        assertFailsWith<IllegalArgumentException> {
            KavitaBaseUrl.normalize("ftp://library.example")
        }
    }

    @Test
    fun authenticateSendsApiKeyHeaderToExpirationEndpoint() = runTest {
        val capturedRequests = mutableListOf<HttpRequestData>()
        val authClient = testClient(capturedRequests = capturedRequests)
        val credentials = SourceCredentials.ApiKey(
            serverUrl = "https://library.example/kavita/",
            apiKey = TestApiKey,
        )

        val result = authClient.authenticate(credentials)

        assertIs<KavitaAuthResult.Success>(result)
        val request = capturedRequests.single()
        assertEquals("https://library.example/kavita/api/plugin/authkey-expires", request.url.toString())
        assertTrue(request.headers[KavitaAuthenticationClient.ApiKeyHeader] == TestApiKey)
    }

    @Test
    fun authenticateReturnsSuccessWhenExpirationIsNull() = runTest {
        val authClient = testClient(responseBody = """{"expiresAt": null}""")

        val result = authClient.authenticate(validCredentials())

        val success = assertIs<KavitaAuthResult.Success>(result)
        assertEquals(KavitaAuthKeyExpiration.DoesNotExpire, success.status.authKeyExpiration)
    }

    @Test
    fun authenticateReturnsSuccessAndPreservesTimestampExpiration() = runTest {
        val authClient = testClient(responseBody = """{"expiresAt": "2026-12-31T00:00:00Z"}""")

        val result = authClient.authenticate(validCredentials())

        val success = assertIs<KavitaAuthResult.Success>(result)
        assertEquals(
            KavitaAuthKeyExpiration.ExpiresAt("2026-12-31T00:00:00Z"),
            success.status.authKeyExpiration,
        )
    }

    @Test
    fun authenticateToleratesRawNullResponse() = runTest {
        val authClient = testClient(responseBody = "null")

        val result = authClient.authenticate(validCredentials())

        val success = assertIs<KavitaAuthResult.Success>(result)
        assertEquals(KavitaAuthKeyExpiration.DoesNotExpire, success.status.authKeyExpiration)
    }

    @Test
    fun authenticateMapsUnauthorizedResponses() = runTest {
        val authClient = testClient(status = HttpStatusCode.Unauthorized)

        val result = authClient.authenticate(validCredentials())

        val failure = assertIs<KavitaAuthResult.Failure>(result)
        assertEquals(SourceError.Unauthorized, failure.error)
    }

    @Test
    fun authenticateMapsForbiddenResponsesToUnauthorized() = runTest {
        val authClient = testClient(status = HttpStatusCode.Forbidden)

        val result = authClient.authenticate(validCredentials())

        val failure = assertIs<KavitaAuthResult.Failure>(result)
        assertEquals(SourceError.Unauthorized, failure.error)
    }

    @Test
    fun authenticateMapsUnexpectedStatusResponses() = runTest {
        val authClient = testClient(
            status = HttpStatusCode.InternalServerError,
            responseBody = "server returned $TestApiKey",
        )

        val result = authClient.authenticate(validCredentials())

        val failure = assertIs<KavitaAuthResult.Failure>(result)
        val error = assertIs<SourceError.UnexpectedResponse>(failure.error)
        assertEquals(500, error.status)
        assertFalse(error.toString().contains(TestApiKey))
        assertTrue(error.bodySnippet?.contains("<redacted>") == true)
    }

    @Test
    fun authenticateMapsNetworkFailures() = runTest {
        val httpClient = HttpClient(
            MockEngine {
                throw IOException("Network unavailable")
            },
        )
        val authClient = KavitaAuthenticationClient(httpClient)

        val result = authClient.authenticate(validCredentials())

        val failure = assertIs<KavitaAuthResult.Failure>(result)
        assertEquals(SourceError.NetworkUnavailable, failure.error)
    }

    @Test
    fun apiKeyDoesNotLeakThroughStringOutputOrReturnedErrors() = runTest {
        val credentials = SourceCredentials.ApiKey(
            serverUrl = "https://library.example",
            apiKey = TestApiKey,
        )
        val authClient = testClient(
            status = HttpStatusCode.BadGateway,
            responseBody = "upstream echoed $TestApiKey",
        )

        val result = authClient.authenticate(credentials)

        assertFalse(credentials.toString().contains(TestApiKey))
        assertFalse(result.toString().contains(TestApiKey))
    }

    @Test
    fun invalidServerUrlIsMappedWithoutCallingHttp() = runTest {
        val authClient = testClient {
            error("HTTP should not be called for invalid server URLs.")
        }

        val result = authClient.authenticate(
            SourceCredentials.ApiKey(
                serverUrl = "library.example",
                apiKey = TestApiKey,
            ),
        )

        val failure = assertIs<KavitaAuthResult.Failure>(result)
        val error = assertIs<SourceError.Unknown>(failure.error)
        assertEquals("Invalid Kavita server URL.", error.message)
        assertFalse(error.toString().contains(TestApiKey))
    }

    @Test
    fun blankCredentialsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            SourceCredentials.ApiKey(
                serverUrl = "https://library.example",
                apiKey = " ",
            )
        }
    }

    private fun testClient(
        status: HttpStatusCode = HttpStatusCode.OK,
        responseBody: String = """{"expiresAt": null}""",
        capturedRequests: MutableList<HttpRequestData> = mutableListOf(),
    ): KavitaAuthenticationClient {
        return testClient { request ->
            capturedRequests += request
            respond(
                content = responseBody,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
    }

    private fun testClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): KavitaAuthenticationClient {
        val httpClient = HttpClient(MockEngine(handler))
        return KavitaAuthenticationClient(httpClient)
    }

    private fun validCredentials(): SourceCredentials.ApiKey {
        return SourceCredentials.ApiKey(
            serverUrl = "https://library.example",
            apiKey = TestApiKey,
        )
    }

    private companion object {
        const val TestApiKey = "kavita-test-api-key"
    }
}
