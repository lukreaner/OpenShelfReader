# Source adapter contract

The source adapter hides backend-specific APIs from the rest of the app.

## Goals

- Kavita first.
- Calibre/Calibre-Web later.
- OPDS fallback only.
- No UI dependency on backend DTOs.

## Core types

```kotlin
@JvmInline
value class SourceId(val value: String)

@JvmInline
value class RemoteLibraryId(val value: String)

@JvmInline
value class RemoteSeriesId(val value: String)

@JvmInline
value class RemoteBookId(val value: String)

@JvmInline
value class RemoteFileId(val value: String)

enum class SourceType {
    KAVITA,
    CALIBRE_WEB,
    CALIBRE,
    OPDS
}

enum class PublicationFormat {
    EPUB,
    PDF,
    CBZ,
    CBR,
    MOBI,
    AZW3,
    UNKNOWN
}
```

## Interface sketch

```kotlin
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
```

## Capability rules

Adapters must truthfully report capabilities. UI and sync must respect these capabilities.

Example:

- Kavita: remote progress read/write should be implemented.
- Calibre-Web: metadata/download first, progress only if a reliable mechanism exists.
- OPDS: browsing/download, progress usually unsupported.

## Error model

Map backend-specific errors into app errors:

```kotlin
sealed interface SourceError {
    data object NetworkUnavailable : SourceError
    data object Unauthorized : SourceError
    data object ApiKeyExpired : SourceError
    data object NotFound : SourceError
    data class UnsupportedServerVersion(val version: String?) : SourceError
    data class UnexpectedResponse(val status: Int, val bodySnippet: String?) : SourceError
    data class Unknown(val message: String?) : SourceError
}
```
