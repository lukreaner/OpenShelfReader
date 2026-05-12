package org.openshelf.reader

import org.openshelf.reader.core.PublicationFormat
import org.openshelf.reader.source.api.RemoteFileId
import org.openshelf.reader.source.api.RemotePublicationFile
import kotlin.test.Test
import kotlin.test.assertEquals

class PublicationFileActionTest {
    @Test
    fun cachedEpubBecomesOpenable() {
        val action = publicationFileAction(
            file = RemotePublicationFile(
                id = RemoteFileId("epub-file"),
                format = PublicationFormat.EPUB,
            ),
            downloadState = FileDownloadUiState.Downloaded(
                localPath = "publication-cache/source/book/file.epub",
                bookIdentityId = "book-identity",
                publicationFileId = "publication-file",
            ),
        )

        assertEquals(PublicationFileAction.OpenEpub, action)
    }

    @Test
    fun cachedPdfDoesNotGetEpubOpenAction() {
        val action = publicationFileAction(
            file = RemotePublicationFile(
                id = RemoteFileId("pdf-file"),
                format = PublicationFormat.PDF,
            ),
            downloadState = FileDownloadUiState.Downloaded(
                localPath = "publication-cache/source/book/file.pdf",
                bookIdentityId = "book-identity",
                publicationFileId = "publication-file",
            ),
        )

        assertEquals(PublicationFileAction.DownloadedUnsupportedReader, action)
    }

    @Test
    fun unsupportedFormatCannotDownloadOrOpen() {
        val action = publicationFileAction(
            file = RemotePublicationFile(
                id = RemoteFileId("mobi-file"),
                format = PublicationFormat.MOBI,
            ),
            downloadState = FileDownloadUiState.Idle,
        )

        assertEquals(PublicationFileAction.UnsupportedFormat, action)
    }
}
