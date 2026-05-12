package org.openshelf.reader.download

import org.openshelf.reader.core.OpenShelfCore
import org.openshelf.reader.core.PublicationFormat
import org.openshelf.reader.source.api.RemoteBookDetails
import org.openshelf.reader.source.api.RemoteDownloadRequest
import org.openshelf.reader.source.api.RemotePublicationFile
import org.openshelf.reader.source.api.SourceAdapter
import org.openshelf.reader.source.api.SourceError
import org.openshelf.reader.storage.BookIdentityId
import org.openshelf.reader.storage.LocalBookIdentity
import org.openshelf.reader.storage.LocalPublicationFile
import org.openshelf.reader.storage.PublicationFileId
import org.openshelf.reader.storage.PublicationFileRepository

class DownloadPublicationFileUseCase(
    private val repository: PublicationFileRepository,
    private val fileStore: PublicationFileStore,
    private val clock: EpochMillisProvider,
) {
    suspend fun download(
        sourceAdapter: SourceAdapter,
        book: RemoteBookDetails,
        file: RemotePublicationFile,
        onProgress: (DownloadProgress) -> Unit = {},
    ): DownloadPublicationFileResult {
        if (file.format !in OpenShelfCore.supportedFormats) {
            return DownloadPublicationFileResult.Failure(
                DownloadPublicationFileError.UnsupportedFormat(file.format),
            )
        }

        onProgress(DownloadProgress.CheckingCache)

        val cached = repository.findPublicationFile(
            sourceId = sourceAdapter.sourceId,
            remoteFileId = file.id,
            format = file.format,
        )
        if (cached != null && isUsableCache(cached, file.sizeBytes)) {
            onProgress(DownloadProgress.Cached(cached))
            return DownloadPublicationFileResult.Cached(cached)
        }

        val now = clock.nowEpochMillis()
        val bookIdentityId = BookIdentityId(
            stableId("book", sourceAdapter.sourceId.value, book.id.value, file.id.value),
        )
        val publicationFileId = PublicationFileId(
            stableId("publication-file", sourceAdapter.sourceId.value, file.id.value, file.format.name),
        )
        val localPath = localPathFor(
            sourceId = sourceAdapter.sourceId.value,
            bookIdentityId = bookIdentityId.value,
            publicationFileId = publicationFileId.value,
            format = file.format,
        )

        onProgress(DownloadProgress.Downloading(bytesWritten = 0L, totalBytes = file.sizeBytes))
        val writeResult = fileStore.writeAtomically(
            relativePath = localPath,
            expectedSizeBytes = file.sizeBytes,
            onBytesWritten = { bytesWritten ->
                onProgress(DownloadProgress.Downloading(bytesWritten = bytesWritten, totalBytes = file.sizeBytes))
            },
        ) { sink ->
            sourceAdapter.downloadFile(
                request = RemoteDownloadRequest(
                    bookId = book.id,
                    fileId = file.id,
                    expectedFormat = file.format,
                    expectedFileName = file.fileName,
                    expectedSizeBytes = file.sizeBytes,
                ),
                sink = sink,
            )
        }

        return when (writeResult) {
            is FileStoreWriteResult.DownloadFailure -> DownloadPublicationFileResult.Failure(
                DownloadPublicationFileError.SourceFailure(writeResult.error),
            )

            is FileStoreWriteResult.SizeMismatch -> DownloadPublicationFileResult.Failure(
                DownloadPublicationFileError.SizeMismatch(
                    expectedBytes = writeResult.expectedBytes,
                    actualBytes = writeResult.actualBytes,
                ),
            )

            is FileStoreWriteResult.StorageFailure -> DownloadPublicationFileResult.Failure(
                DownloadPublicationFileError.StorageFailure(writeResult.message),
            )

            is FileStoreWriteResult.Success -> storeDownloadedFile(
                sourceAdapter = sourceAdapter,
                book = book,
                file = file,
                bookIdentityId = bookIdentityId,
                publicationFileId = publicationFileId,
                localPath = localPath,
                bytesWritten = writeResult.bytesWritten,
                now = now,
                onProgress = onProgress,
            )
        }
    }

    private suspend fun isUsableCache(
        file: LocalPublicationFile,
        expectedSizeBytes: Long?,
    ): Boolean {
        if (!fileStore.exists(file.localPath)) return false
        val expectedSize = expectedSizeBytes ?: file.fileSize ?: return true
        return fileStore.sizeBytes(file.localPath) == expectedSize
    }

    private suspend fun storeDownloadedFile(
        sourceAdapter: SourceAdapter,
        book: RemoteBookDetails,
        file: RemotePublicationFile,
        bookIdentityId: BookIdentityId,
        publicationFileId: PublicationFileId,
        localPath: String,
        bytesWritten: Long,
        now: Long,
        onProgress: (DownloadProgress) -> Unit,
    ): DownloadPublicationFileResult {
        val identity = LocalBookIdentity(
            id = bookIdentityId,
            sourceId = sourceAdapter.sourceId,
            remoteBookId = book.id,
            remoteFileId = file.id,
            fileSize = file.sizeBytes ?: bytesWritten,
            titleNormalized = normalize(book.title),
            authorNormalized = normalize(book.authors.joinToString(" ")),
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        val localFile = LocalPublicationFile(
            id = publicationFileId,
            bookIdentityId = bookIdentityId,
            sourceId = sourceAdapter.sourceId,
            remoteFileId = file.id,
            localPath = localPath,
            format = file.format,
            fileSize = file.sizeBytes ?: bytesWritten,
            downloadedAtEpochMillis = now,
        )

        repository.upsertCachedPublication(identity, localFile)
        onProgress(DownloadProgress.Downloaded(localFile))
        return DownloadPublicationFileResult.Downloaded(localFile)
    }
}

fun interface EpochMillisProvider {
    fun nowEpochMillis(): Long
}

sealed interface DownloadProgress {
    data object CheckingCache : DownloadProgress

    data class Downloading(
        val bytesWritten: Long,
        val totalBytes: Long?,
    ) : DownloadProgress {
        init {
            require(bytesWritten >= 0) { "Downloaded byte count must not be negative." }
            require(totalBytes == null || totalBytes >= 0) { "Total byte count must not be negative." }
        }
    }

    data class Cached(val file: LocalPublicationFile) : DownloadProgress
    data class Downloaded(val file: LocalPublicationFile) : DownloadProgress
}

sealed interface DownloadPublicationFileResult {
    data class Cached(val file: LocalPublicationFile) : DownloadPublicationFileResult
    data class Downloaded(val file: LocalPublicationFile) : DownloadPublicationFileResult
    data class Failure(val error: DownloadPublicationFileError) : DownloadPublicationFileResult
}

sealed interface DownloadPublicationFileError {
    data class UnsupportedFormat(val format: PublicationFormat) : DownloadPublicationFileError
    data class SourceFailure(val error: SourceError) : DownloadPublicationFileError
    data class SizeMismatch(val expectedBytes: Long, val actualBytes: Long) : DownloadPublicationFileError
    data class StorageFailure(val message: String?) : DownloadPublicationFileError
}

private fun stableId(prefix: String, vararg parts: String): String =
    listOf(prefix)
        .plus(parts.toList())
        .joinToString(":")

private fun localPathFor(
    sourceId: String,
    bookIdentityId: String,
    publicationFileId: String,
    format: PublicationFormat,
): String {
    val extension = when (format) {
        PublicationFormat.EPUB -> "epub"
        PublicationFormat.PDF -> "pdf"
        else -> format.name.lowercase()
    }

    return listOf(
        "publication-cache",
        sourceId.toPathSegment(),
        bookIdentityId.toPathSegment(),
        "${publicationFileId.toPathSegment()}.$extension",
    ).joinToString("/")
}

private fun String.toPathSegment(): String {
    val sanitized = map { character ->
        if (character.isLetterOrDigit() || character == '-' || character == '_' || character == '.') {
            character
        } else {
            '_'
        }
    }.joinToString("").trim('_', '.', '-')

    return sanitized.ifBlank { "item" }
}

private fun normalize(value: String): String? =
    value
        .trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .takeIf { it.isNotBlank() }
