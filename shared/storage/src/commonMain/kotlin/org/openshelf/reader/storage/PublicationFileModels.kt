package org.openshelf.reader.storage

import org.openshelf.reader.core.PublicationFormat
import org.openshelf.reader.source.api.RemoteBookId
import org.openshelf.reader.source.api.RemoteFileId
import org.openshelf.reader.source.api.SourceId

@JvmInline
value class PublicationFileId(val value: String) {
    init {
        require(value.isNotBlank()) { "Publication file id must not be blank." }
    }
}

data class LocalBookIdentity(
    val id: BookIdentityId,
    val sourceId: SourceId,
    val remoteBookId: RemoteBookId? = null,
    val remoteFileId: RemoteFileId? = null,
    val fileHash: String? = null,
    val fileSize: Long? = null,
    val epubIdentifier: String? = null,
    val titleNormalized: String? = null,
    val authorNormalized: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(fileHash == null || fileHash.isNotBlank()) { "File hash must not be blank." }
        require(fileSize == null || fileSize >= 0) { "File size must not be negative." }
        require(epubIdentifier == null || epubIdentifier.isNotBlank()) { "EPUB identifier must not be blank." }
        require(titleNormalized == null || titleNormalized.isNotBlank()) { "Normalized title must not be blank." }
        require(authorNormalized == null || authorNormalized.isNotBlank()) { "Normalized author must not be blank." }
        require(createdAtEpochMillis >= 0) { "Created timestamp must not be negative." }
        require(updatedAtEpochMillis >= 0) { "Updated timestamp must not be negative." }
    }
}

data class LocalPublicationFile(
    val id: PublicationFileId,
    val bookIdentityId: BookIdentityId,
    val sourceId: SourceId,
    val remoteFileId: RemoteFileId,
    val localPath: String,
    val format: PublicationFormat,
    val fileSize: Long? = null,
    val fileHash: String? = null,
    val downloadedAtEpochMillis: Long,
    val lastOpenedAtEpochMillis: Long? = null,
) {
    init {
        require(localPath.isNotBlank()) { "Local path must not be blank." }
        require(fileSize == null || fileSize >= 0) { "File size must not be negative." }
        require(fileHash == null || fileHash.isNotBlank()) { "File hash must not be blank." }
        require(downloadedAtEpochMillis >= 0) { "Downloaded timestamp must not be negative." }
        require(lastOpenedAtEpochMillis == null || lastOpenedAtEpochMillis >= 0) {
            "Last opened timestamp must not be negative."
        }
    }
}
