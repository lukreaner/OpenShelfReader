package org.openshelf.reader.source.api

interface SourceAdapter {
    val sourceId: SourceId
    val sourceType: SourceType
    val capabilities: SourceCapabilities

    suspend fun authenticate(credentials: SourceCredentials): AuthResult
    suspend fun refreshCapabilities(): SourceCapabilities

    suspend fun listLibraries(): List<RemoteLibrary>
    suspend fun listSeries(libraryId: RemoteLibraryId): List<RemoteSeries>
    suspend fun listBooks(seriesId: RemoteSeriesId): List<RemoteBook>
    suspend fun search(query: String): List<RemoteSearchResult>

    suspend fun getBook(bookId: RemoteBookId): RemoteBookDetails
    suspend fun getCover(bookId: RemoteBookId): ByteArray?
    suspend fun downloadFile(fileId: RemoteFileId, sink: DownloadSink): DownloadResult

    suspend fun getRemoteProgress(bookId: RemoteBookId): RemoteProgress?
    suspend fun setRemoteProgress(bookId: RemoteBookId, progress: RemoteProgress): ProgressWriteResult
}
