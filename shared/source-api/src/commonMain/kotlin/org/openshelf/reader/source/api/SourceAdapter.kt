package org.openshelf.reader.source.api

interface SourceAdapter {
    val sourceId: SourceId
    val sourceType: SourceType
    val capabilities: SourceCapabilities

    /**
     * Methods that do not wrap their result in a typed result object throw [SourceAdapterException]
     * when the source rejects a request, returns an unusable response, or does not support the operation.
     */
    suspend fun authenticate(credentials: SourceCredentials): AuthResult
    suspend fun refreshCapabilities(): SourceCapabilities

    suspend fun listLibraries(): List<RemoteLibrary>
    suspend fun listSeries(libraryId: RemoteLibraryId): List<RemoteSeries>
    suspend fun listBooks(seriesId: RemoteSeriesId): List<RemoteBook>
    suspend fun search(query: String): List<RemoteSearchResult>

    suspend fun getBook(bookId: RemoteBookId): RemoteBookDetails
    suspend fun getCover(bookId: RemoteBookId): ByteArray?
    suspend fun downloadFile(request: RemoteDownloadRequest, sink: DownloadSink): DownloadResult

    suspend fun getRemoteProgress(bookId: RemoteBookId): RemoteProgress?
    suspend fun setRemoteProgress(bookId: RemoteBookId, progress: RemoteProgress): ProgressWriteResult
}
