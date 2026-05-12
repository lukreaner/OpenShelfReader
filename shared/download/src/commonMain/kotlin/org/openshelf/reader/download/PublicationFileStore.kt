package org.openshelf.reader.download

import org.openshelf.reader.source.api.DownloadResult
import org.openshelf.reader.source.api.DownloadSink
import org.openshelf.reader.source.api.SourceError

interface PublicationFileStore {
    suspend fun exists(relativePath: String): Boolean

    suspend fun sizeBytes(relativePath: String): Long?

    suspend fun delete(relativePath: String)

    suspend fun writeAtomically(
        relativePath: String,
        expectedSizeBytes: Long?,
        onBytesWritten: (Long) -> Unit = {},
        downloader: suspend (DownloadSink) -> DownloadResult,
    ): FileStoreWriteResult
}

sealed interface FileStoreWriteResult {
    data class Success(
        val bytesWritten: Long,
        val download: DownloadResult.Success,
    ) : FileStoreWriteResult

    data class DownloadFailure(val error: SourceError) : FileStoreWriteResult

    data class SizeMismatch(
        val expectedBytes: Long,
        val actualBytes: Long,
    ) : FileStoreWriteResult

    data class StorageFailure(val message: String?) : FileStoreWriteResult
}
