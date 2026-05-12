package org.openshelf.reader.download

import android.content.Context
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openshelf.reader.source.api.DownloadResult

internal class AndroidPublicationFileStore(
    private val rootDirectory: File,
) : PublicationFileStore {
    constructor(context: Context) : this(context.filesDir)

    override suspend fun exists(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        resolve(relativePath).isFile
    }

    override suspend fun sizeBytes(relativePath: String): Long? = withContext(Dispatchers.IO) {
        resolve(relativePath).takeIf { it.isFile }?.length()
    }

    override suspend fun delete(relativePath: String) {
        withContext(Dispatchers.IO) {
            resolve(relativePath).delete()
        }
    }

    override suspend fun writeAtomically(
        relativePath: String,
        expectedSizeBytes: Long?,
        onBytesWritten: (Long) -> Unit,
        downloader: suspend (org.openshelf.reader.source.api.DownloadSink) -> DownloadResult,
    ): FileStoreWriteResult = withContext(Dispatchers.IO) {
        val target = resolve(relativePath)
        val parent = target.parentFile ?: return@withContext FileStoreWriteResult.StorageFailure("Missing cache directory.")
        if (!parent.exists() && !parent.mkdirs()) {
            return@withContext FileStoreWriteResult.StorageFailure("Could not create cache directory.")
        }

        val temporary = File(parent, "${target.name}.tmp")
        temporary.delete()

        var bytesWritten = 0L
        try {
            val output = temporary.outputStream()
            val result = try {
                downloader { bytes ->
                    output.write(bytes)
                    bytesWritten += bytes.size
                    onBytesWritten(bytesWritten)
                }
            } finally {
                output.close()
            }

            when (result) {
                is DownloadResult.Failure -> {
                    temporary.delete()
                    FileStoreWriteResult.DownloadFailure(result.error)
                }

                is DownloadResult.Success -> {
                    if (expectedSizeBytes != null && expectedSizeBytes != bytesWritten) {
                        temporary.delete()
                        return@withContext FileStoreWriteResult.SizeMismatch(
                            expectedBytes = expectedSizeBytes,
                            actualBytes = bytesWritten,
                        )
                    }

                    Files.move(
                        temporary.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                    FileStoreWriteResult.Success(bytesWritten = bytesWritten, download = result)
                }
            }
        } catch (error: CancellationException) {
            temporary.delete()
            throw error
        } catch (error: IOException) {
            temporary.delete()
            FileStoreWriteResult.StorageFailure(error.message)
        } catch (error: Exception) {
            temporary.delete()
            FileStoreWriteResult.StorageFailure("Could not save downloaded file.")
        }
    }

    private fun resolve(relativePath: String): File {
        val normalized = relativePath.replace('\\', '/')
        require(normalized.isNotBlank()) { "Relative path must not be blank." }
        require(!normalized.startsWith("/") && !normalized.contains("..")) {
            "Relative path must stay inside the app cache directory."
        }
        return File(rootDirectory, normalized)
    }
}
