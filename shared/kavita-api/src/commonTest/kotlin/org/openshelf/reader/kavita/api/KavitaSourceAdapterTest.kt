package org.openshelf.reader.kavita.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import org.openshelf.reader.source.api.DownloadResult
import org.openshelf.reader.source.api.ProgressWriteResult
import org.openshelf.reader.source.api.RemoteBookId
import org.openshelf.reader.source.api.RemoteFileId
import org.openshelf.reader.source.api.RemoteProgress
import org.openshelf.reader.source.api.SourceAdapterException
import org.openshelf.reader.source.api.SourceError
import org.openshelf.reader.source.api.SourceId
import org.openshelf.reader.source.api.SourceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KavitaSourceAdapterTest {
    @Test
    fun declaresOnlyImplementedBrowsingCapabilities() {
        val adapter = adapter()

        assertEquals(SourceType.KAVITA, adapter.sourceType)
        assertTrue(adapter.capabilities.supportsLibraryBrowsing)
        assertTrue(adapter.capabilities.supportsSeriesMetadata)
        assertFalse(adapter.capabilities.supportsSearch)
        assertFalse(adapter.capabilities.supportsDownloads)
        assertFalse(adapter.capabilities.supportsRemoteProgressRead)
        assertFalse(adapter.capabilities.supportsRemoteProgressWrite)
    }

    @Test
    fun unimplementedOperationsDoNotReturnFakeEmptySuccess() = runTest {
        val adapter = adapter()

        val searchException = assertFailsWith<SourceAdapterException> {
            adapter.search("leviathan")
        }
        assertIs<SourceError.UnsupportedOperation>(searchException.error)

        val progressException = assertFailsWith<SourceAdapterException> {
            adapter.getRemoteProgress(RemoteBookId("301"))
        }
        assertIs<SourceError.UnsupportedOperation>(progressException.error)

        val downloadResult = adapter.downloadFile(RemoteFileId("401")) { _ -> }
        val downloadFailure = assertIs<DownloadResult.Failure>(downloadResult)
        assertIs<SourceError.UnsupportedOperation>(downloadFailure.error)

        val writeResult = adapter.setRemoteProgress(
            bookId = RemoteBookId("301"),
            progress = RemoteProgress(bookId = RemoteBookId("301")),
        )
        val writeFailure = assertIs<ProgressWriteResult.Failure>(writeResult)
        assertIs<SourceError.UnsupportedOperation>(writeFailure.error)
    }

    @Test
    fun apiKeyDoesNotLeakFromAdapterStringOutput() {
        val adapter = adapter()

        assertFalse(adapter.toString().contains(TestApiKey))
        assertTrue(adapter.toString().contains(KavitaRedactedSecret))
    }

    private fun adapter(): KavitaSourceAdapter {
        val httpClient = HttpClient(
            MockEngine {
                respond("""{"expiresAt": null}""")
            },
        )
        return KavitaSourceAdapter(
            httpClient = httpClient,
            sourceId = SourceId("kavita-source"),
            serverUrl = "https://library.example/kavita",
            apiKey = TestApiKey,
        )
    }

    private companion object {
        const val TestApiKey = "kavita-test-api-key"
    }
}
