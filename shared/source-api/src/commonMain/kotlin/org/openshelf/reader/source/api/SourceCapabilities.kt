package org.openshelf.reader.source.api

data class SourceCapabilities(
    val supportsLibraryBrowsing: Boolean = false,
    val supportsSearch: Boolean = false,
    val supportsDownloads: Boolean = false,
    val supportsRemoteProgressRead: Boolean = false,
    val supportsRemoteProgressWrite: Boolean = false,
    val supportsCollections: Boolean = false,
    val supportsSeriesMetadata: Boolean = false,
)
