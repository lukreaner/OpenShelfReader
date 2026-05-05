package org.openshelf.reader.kavita.api

import io.ktor.client.HttpClient
import org.openshelf.reader.source.api.AuthResult
import org.openshelf.reader.source.api.DownloadResult
import org.openshelf.reader.source.api.DownloadSink
import org.openshelf.reader.source.api.ProgressWriteResult
import org.openshelf.reader.source.api.RemoteBook
import org.openshelf.reader.source.api.RemoteBookDetails
import org.openshelf.reader.source.api.RemoteBookId
import org.openshelf.reader.source.api.RemoteFileId
import org.openshelf.reader.source.api.RemoteLibrary
import org.openshelf.reader.source.api.RemoteLibraryId
import org.openshelf.reader.source.api.RemoteProgress
import org.openshelf.reader.source.api.RemoteSearchResult
import org.openshelf.reader.source.api.RemoteSeries
import org.openshelf.reader.source.api.RemoteSeriesId
import org.openshelf.reader.source.api.SourceAdapter
import org.openshelf.reader.source.api.SourceAdapterException
import org.openshelf.reader.source.api.SourceCapabilities
import org.openshelf.reader.source.api.SourceCredentials
import org.openshelf.reader.source.api.SourceError
import org.openshelf.reader.source.api.SourceId
import org.openshelf.reader.source.api.SourceType

class KavitaSourceAdapter(
    httpClient: HttpClient,
    override val sourceId: SourceId,
    private val baseUrl: KavitaBaseUrl,
    private val apiKey: String,
    private val authenticationClient: KavitaAuthenticationClient = KavitaAuthenticationClient(httpClient),
    private val browsingClient: KavitaBrowsingClient = KavitaBrowsingClient(
        httpClient = httpClient,
        sourceId = sourceId,
        baseUrl = baseUrl,
        apiKey = apiKey,
    ),
) : SourceAdapter {
    constructor(
        httpClient: HttpClient,
        sourceId: SourceId,
        serverUrl: String,
        apiKey: String,
    ) : this(
        httpClient = httpClient,
        sourceId = sourceId,
        baseUrl = KavitaBaseUrl.normalize(serverUrl),
        apiKey = apiKey,
    )

    init {
        require(apiKey.isNotBlank()) { "Kavita API key must not be blank." }
    }

    override val sourceType: SourceType = SourceType.KAVITA

    override val capabilities: SourceCapabilities = SourceCapabilities(
        supportsLibraryBrowsing = true,
        supportsSearch = false,
        supportsDownloads = false,
        supportsRemoteProgressRead = false,
        supportsRemoteProgressWrite = false,
        supportsCollections = false,
        supportsSeriesMetadata = true,
    )

    override suspend fun authenticate(credentials: SourceCredentials): AuthResult {
        return when (val result = authenticationClient.authenticate(credentials)) {
            is KavitaAuthResult.Success -> AuthResult.Success
            is KavitaAuthResult.Failure -> AuthResult.Failure(result.error)
        }
    }

    override suspend fun refreshCapabilities(): SourceCapabilities = capabilities

    override suspend fun listLibraries(): List<RemoteLibrary> {
        return browsingClient.listLibraries()
    }

    override suspend fun listSeries(libraryId: RemoteLibraryId): List<RemoteSeries> {
        return browsingClient.listSeries(libraryId)
    }

    override suspend fun listBooks(seriesId: RemoteSeriesId): List<RemoteBook> {
        return browsingClient.listBooks(seriesId)
    }

    override suspend fun search(query: String): List<RemoteSearchResult> {
        unsupported("search")
    }

    override suspend fun getBook(bookId: RemoteBookId): RemoteBookDetails {
        return browsingClient.getBook(bookId)
    }

    override suspend fun getCover(bookId: RemoteBookId): ByteArray? {
        return browsingClient.getCover(bookId)
    }

    override suspend fun downloadFile(
        fileId: RemoteFileId,
        sink: DownloadSink,
    ): DownloadResult {
        return DownloadResult.Failure(unsupportedError("downloadFile"))
    }

    override suspend fun getRemoteProgress(bookId: RemoteBookId): RemoteProgress? {
        unsupported("getRemoteProgress")
    }

    override suspend fun setRemoteProgress(
        bookId: RemoteBookId,
        progress: RemoteProgress,
    ): ProgressWriteResult {
        return ProgressWriteResult.Failure(unsupportedError("setRemoteProgress"))
    }

    override fun toString(): String {
        return "KavitaSourceAdapter(sourceId=$sourceId, sourceType=$sourceType, baseUrl=$baseUrl, apiKey=$KavitaRedactedSecret)"
    }

    private fun unsupported(operation: String): Nothing {
        throw SourceAdapterException(unsupportedError(operation))
    }

    private fun unsupportedError(operation: String): SourceError {
        return SourceError.UnsupportedOperation("KavitaSourceAdapter.$operation")
    }
}
