package org.openshelf.reader

import org.openshelf.reader.core.PublicationFormat
import org.openshelf.reader.download.DownloadPublicationFileError
import org.openshelf.reader.source.api.SourceAdapterException
import org.openshelf.reader.source.api.SourceError
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class KavitaUiMessageMapperTest {
    @Test
    fun mapsUnauthorizedWithoutDisplayingApiKey() {
        val message = messageForSourceError(
            error = SourceError.Unauthorized,
            apiKey = TestApiKey,
        )

        assertEquals("The Kavita server rejected this API key. Check the key and try again.", message)
        assertFalse(message.contains(TestApiKey))
    }

    @Test
    fun mapsUnexpectedResponseWithoutDisplayingBodySnippet() {
        val message = messageForThrowable(
            error = SourceAdapterException(
                SourceError.UnexpectedResponse(
                    status = 500,
                    bodySnippet = "server replied with $TestApiKey",
                ),
            ),
            apiKey = TestApiKey,
        )

        assertContains(message, "HTTP 500")
        assertFalse(message.contains(TestApiKey))
        assertFalse(message.contains("server replied"))
    }

    @Test
    fun mapsUnknownThrowableWithoutDisplayingRawExceptionText() {
        val message = messageForThrowable(
            error = IllegalStateException("failure included $TestApiKey"),
            apiKey = TestApiKey,
        )

        assertEquals("Something went wrong while talking to Kavita. Check the server URL and try again.", message)
        assertFalse(message.contains(TestApiKey))
    }

    @Test
    fun redactsApiKeyFromFallbackMessages() {
        val message = "Do not show $TestApiKey here".redactApiKey(TestApiKey)

        assertEquals("Do not show <redacted> here", message)
    }

    @Test
    fun mapsUnsupportedDownloadFormat() {
        val message = messageForDownloadError(
            error = DownloadPublicationFileError.UnsupportedFormat(PublicationFormat.MOBI),
            apiKey = TestApiKey,
        )

        assertEquals("Only EPUB and PDF downloads are supported in this alpha.", message)
    }

    @Test
    fun mapsDownloadSizeMismatchWithoutRawDetails() {
        val message = messageForDownloadError(
            error = DownloadPublicationFileError.SizeMismatch(expectedBytes = 10L, actualBytes = 3L),
            apiKey = TestApiKey,
        )

        assertEquals("The download did not match the expected file size. Try again.", message)
    }

    private companion object {
        const val TestApiKey = "kavita-test-api-key"
    }
}
