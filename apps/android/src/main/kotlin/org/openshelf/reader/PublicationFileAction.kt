package org.openshelf.reader

import org.openshelf.reader.core.OpenShelfCore
import org.openshelf.reader.core.PublicationFormat
import org.openshelf.reader.source.api.RemotePublicationFile

internal enum class PublicationFileAction {
    Download,
    Busy,
    OpenEpub,
    DownloadedUnsupportedReader,
    UnsupportedFormat,
}

internal fun publicationFileAction(
    file: RemotePublicationFile,
    downloadState: FileDownloadUiState,
): PublicationFileAction {
    if (file.format !in OpenShelfCore.supportedFormats) {
        return PublicationFileAction.UnsupportedFormat
    }

    return when (downloadState) {
        FileDownloadUiState.Checking,
        is FileDownloadUiState.Downloading,
        -> PublicationFileAction.Busy

        is FileDownloadUiState.Downloaded -> {
            if (file.format == PublicationFormat.EPUB) {
                PublicationFileAction.OpenEpub
            } else {
                PublicationFileAction.DownloadedUnsupportedReader
            }
        }

        FileDownloadUiState.Idle,
        is FileDownloadUiState.Error,
        -> PublicationFileAction.Download
    }
}
