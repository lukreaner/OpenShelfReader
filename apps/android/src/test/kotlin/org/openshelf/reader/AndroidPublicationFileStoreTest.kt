package org.openshelf.reader

import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.openshelf.reader.core.PublicationFormat
import org.openshelf.reader.download.AndroidPublicationFileStore
import org.openshelf.reader.download.FileStoreWriteResult
import org.openshelf.reader.source.api.DownloadResult
import org.openshelf.reader.source.api.RemoteFileId
import org.openshelf.reader.source.api.SourceError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AndroidPublicationFileStoreTest {
    @Test
    fun writesAndReadsRelativeFileAtomically() = runTest {
        val root = Files.createTempDirectory("openshelf-store").toFile()
        val store = AndroidPublicationFileStore(root)

        val result = store.writeAtomically(
            relativePath = "publication-cache/source/book/file.epub",
            expectedSizeBytes = 3L,
        ) { sink ->
            sink.write(byteArrayOf(1, 2, 3))
            DownloadResult.Success(
                fileId = RemoteFileId("file-1"),
                format = PublicationFormat.EPUB,
                bytesWritten = 3L,
            )
        }

        assertIs<FileStoreWriteResult.Success>(result)
        assertTrue(store.exists("publication-cache/source/book/file.epub"))
        assertEquals(3L, store.sizeBytes("publication-cache/source/book/file.epub"))
    }

    @Test
    fun sizeMismatchLeavesExistingTargetUntouched() = runTest {
        val root = Files.createTempDirectory("openshelf-store").toFile()
        val store = AndroidPublicationFileStore(root)
        val path = "publication-cache/source/book/file.epub"
        store.writeAtomically(path, expectedSizeBytes = 3L) { sink ->
            sink.write(byteArrayOf(9, 9, 9))
            DownloadResult.Success(RemoteFileId("file-1"), PublicationFormat.EPUB, 3L)
        }

        val result = store.writeAtomically(path, expectedSizeBytes = 4L) { sink ->
            sink.write(byteArrayOf(1, 2))
            DownloadResult.Success(RemoteFileId("file-1"), PublicationFormat.EPUB, 2L)
        }

        assertEquals(FileStoreWriteResult.SizeMismatch(4L, 2L), result)
        assertEquals(3L, store.sizeBytes(path))
    }

    @Test
    fun sourceFailureDoesNotCommitTemporaryFile() = runTest {
        val root = Files.createTempDirectory("openshelf-store").toFile()
        val store = AndroidPublicationFileStore(root)
        val path = "publication-cache/source/book/file.epub"

        val result = store.writeAtomically(path, expectedSizeBytes = 3L) { sink ->
            sink.write(byteArrayOf(1, 2))
            DownloadResult.Failure(SourceError.NetworkUnavailable)
        }

        assertEquals(FileStoreWriteResult.DownloadFailure(SourceError.NetworkUnavailable), result)
        assertFalse(store.exists(path))
    }
}
