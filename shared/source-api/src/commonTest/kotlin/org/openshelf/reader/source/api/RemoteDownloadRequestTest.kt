package org.openshelf.reader.source.api

import org.openshelf.reader.core.PublicationFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RemoteDownloadRequestTest {
    @Test
    fun keepsBookAndFileIdentitySeparate() {
        val request = RemoteDownloadRequest(
            bookId = RemoteBookId("chapter-301"),
            fileId = RemoteFileId("file-401"),
            expectedFormat = PublicationFormat.EPUB,
            expectedFileName = "Leviathan Wakes.epub",
            expectedSizeBytes = 123_456L,
        )

        assertEquals(RemoteBookId("chapter-301"), request.bookId)
        assertEquals(RemoteFileId("file-401"), request.fileId)
        assertEquals(PublicationFormat.EPUB, request.expectedFormat)
        assertEquals("Leviathan Wakes.epub", request.expectedFileName)
        assertEquals(123_456L, request.expectedSizeBytes)
    }

    @Test
    fun allowsKnownAndFuturePublicationFormats() {
        PublicationFormat.entries.forEach { format ->
            RemoteDownloadRequest(
                bookId = RemoteBookId("book-$format"),
                fileId = RemoteFileId("file-$format"),
                expectedFormat = format,
            )
        }
    }

    @Test
    fun rejectsInvalidOptionalMetadata() {
        assertFailsWith<IllegalArgumentException> {
            RemoteDownloadRequest(
                bookId = RemoteBookId("book"),
                fileId = RemoteFileId("file"),
                expectedFormat = PublicationFormat.EPUB,
                expectedFileName = " ",
            )
        }

        assertFailsWith<IllegalArgumentException> {
            RemoteDownloadRequest(
                bookId = RemoteBookId("book"),
                fileId = RemoteFileId("file"),
                expectedFormat = PublicationFormat.EPUB,
                expectedSizeBytes = -1L,
            )
        }
    }
}
