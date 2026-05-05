package org.openshelf.reader.source.api

import org.openshelf.reader.core.PublicationFormat

data class RemoteLibrary(
    val id: RemoteLibraryId,
    val sourceId: SourceId,
    val name: String,
) {
    init {
        require(name.isNotBlank()) { "Remote library name must not be blank." }
    }
}

data class RemoteSeries(
    val id: RemoteSeriesId,
    val sourceId: SourceId,
    val libraryId: RemoteLibraryId,
    val title: String,
    val authors: List<String> = emptyList(),
    val bookCount: Int? = null,
) {
    init {
        require(title.isNotBlank()) { "Remote series title must not be blank." }
        require(bookCount == null || bookCount >= 0) { "Remote series book count must not be negative." }
    }
}

data class RemoteBook(
    val id: RemoteBookId,
    val sourceId: SourceId,
    val title: String,
    val authors: List<String> = emptyList(),
    val libraryId: RemoteLibraryId? = null,
    val seriesId: RemoteSeriesId? = null,
    val formats: Set<PublicationFormat> = emptySet(),
) {
    init {
        require(title.isNotBlank()) { "Remote book title must not be blank." }
    }
}

data class RemoteBookDetails(
    val id: RemoteBookId,
    val sourceId: SourceId,
    val title: String,
    val authors: List<String> = emptyList(),
    val libraryId: RemoteLibraryId? = null,
    val seriesId: RemoteSeriesId? = null,
    val formats: Set<PublicationFormat> = emptySet(),
    val files: List<RemotePublicationFile> = emptyList(),
    val summary: String? = null,
    val publishedYear: Int? = null,
) {
    init {
        require(title.isNotBlank()) { "Remote book title must not be blank." }
    }
}

data class RemotePublicationFile(
    val id: RemoteFileId,
    val format: PublicationFormat,
    val fileName: String? = null,
    val sizeBytes: Long? = null,
) {
    init {
        require(sizeBytes == null || sizeBytes >= 0) { "Remote file size must not be negative." }
    }
}

data class RemoteSearchResult(
    val book: RemoteBook,
    val score: Double? = null,
) {
    init {
        require(score == null || score >= 0.0) { "Remote search score must not be negative." }
    }
}

data class RemoteProgress(
    val bookId: RemoteBookId,
    val progression: Double? = null,
    val locator: String? = null,
    val chapterHref: String? = null,
    val chapterIndex: Int? = null,
    val chapterProgression: Double? = null,
    val pdfPageIndex: Int? = null,
    val pdfPageOffset: Double? = null,
    val updatedAtEpochMillis: Long? = null,
    val finished: Boolean = false,
) {
    init {
        require(progression == null || progression in 0.0..1.0) { "Remote progression must be between 0.0 and 1.0." }
        require(chapterIndex == null || chapterIndex >= 0) { "Remote chapter index must not be negative." }
        require(chapterProgression == null || chapterProgression in 0.0..1.0) {
            "Remote chapter progression must be between 0.0 and 1.0."
        }
        require(pdfPageIndex == null || pdfPageIndex >= 0) { "Remote PDF page index must not be negative." }
        require(pdfPageOffset == null || pdfPageOffset in 0.0..1.0) {
            "Remote PDF page offset must be between 0.0 and 1.0."
        }
        require(updatedAtEpochMillis == null || updatedAtEpochMillis >= 0) {
            "Remote progress timestamp must not be negative."
        }
    }
}

fun interface DownloadSink {
    suspend fun write(bytes: ByteArray)
}

sealed interface DownloadResult {
    data class Success(
        val fileId: RemoteFileId,
        val format: PublicationFormat,
        val bytesWritten: Long? = null,
    ) : DownloadResult {
        init {
            require(bytesWritten == null || bytesWritten >= 0) { "Downloaded byte count must not be negative." }
        }
    }

    data class Failure(val error: SourceError) : DownloadResult
}

sealed interface AuthResult {
    data object Success : AuthResult
    data class Failure(val error: SourceError) : AuthResult
}

sealed interface ProgressWriteResult {
    data object Success : ProgressWriteResult
    data class Failure(val error: SourceError) : ProgressWriteResult
}
