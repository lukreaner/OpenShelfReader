package org.openshelf.reader.storage

import app.cash.sqldelight.db.SqlDriver
import org.openshelf.reader.core.PublicationFormat
import org.openshelf.reader.source.api.RemoteBookId
import org.openshelf.reader.source.api.RemoteFileId
import org.openshelf.reader.source.api.SourceId

class SqlDelightPublicationFileRepository(
    private val database: OpenShelfDatabase,
) : PublicationFileRepository {
    constructor(driver: SqlDriver) : this(OpenShelfDatabase(driver))

    override suspend fun upsertBookIdentity(identity: LocalBookIdentity) {
        val existing = getBookIdentity(identity.id)
        val identityToStore = existing?.let {
            identity.copy(createdAtEpochMillis = it.createdAtEpochMillis)
        } ?: identity

        if (existing == null) {
            insertBookIdentity(identityToStore)
        } else {
            updateBookIdentity(identityToStore)
        }
    }

    override suspend fun getBookIdentity(id: BookIdentityId): LocalBookIdentity? =
        database.storageQueries
            .selectBookIdentityById(id.value, ::mapBookIdentity)
            .executeAsOneOrNull()

    override suspend fun upsertPublicationFile(file: LocalPublicationFile) {
        val existing = getPublicationFile(file.id)
        val fileToStore = if (existing?.lastOpenedAtEpochMillis != null && file.lastOpenedAtEpochMillis == null) {
            file.copy(lastOpenedAtEpochMillis = existing.lastOpenedAtEpochMillis)
        } else {
            file
        }

        if (existing == null) {
            insertPublicationFile(fileToStore)
        } else {
            updatePublicationFile(fileToStore)
        }
    }

    override suspend fun upsertCachedPublication(
        identity: LocalBookIdentity,
        file: LocalPublicationFile,
    ) {
        database.transaction {
            val existingIdentity = database.storageQueries
                .selectBookIdentityById(identity.id.value, ::mapBookIdentity)
                .executeAsOneOrNull()
            val identityToStore = existingIdentity?.let {
                identity.copy(createdAtEpochMillis = it.createdAtEpochMillis)
            } ?: identity

            if (existingIdentity == null) {
                insertBookIdentity(identityToStore)
            } else {
                updateBookIdentity(identityToStore)
            }

            val existingFile = database.storageQueries
                .selectPublicationFileById(file.id.value, ::mapPublicationFile)
                .executeAsOneOrNull()
            val fileToStore = if (existingFile?.lastOpenedAtEpochMillis != null && file.lastOpenedAtEpochMillis == null) {
                file.copy(lastOpenedAtEpochMillis = existingFile.lastOpenedAtEpochMillis)
            } else {
                file
            }

            if (existingFile == null) {
                insertPublicationFile(fileToStore)
            } else {
                updatePublicationFile(fileToStore)
            }
        }
    }

    override suspend fun getPublicationFile(id: PublicationFileId): LocalPublicationFile? =
        database.storageQueries
            .selectPublicationFileById(id.value, ::mapPublicationFile)
            .executeAsOneOrNull()

    override suspend fun findPublicationFile(
        sourceId: SourceId,
        remoteFileId: RemoteFileId,
        format: PublicationFormat,
    ): LocalPublicationFile? =
        database.storageQueries
            .selectPublicationFileByRemoteFile(
                source_id = sourceId.value,
                remote_file_id = remoteFileId.value,
                format = format.toPublicationFileStorageValue(),
                mapper = ::mapPublicationFile,
            )
            .executeAsOneOrNull()

    private fun insertBookIdentity(identity: LocalBookIdentity) {
        database.storageQueries.insertBookIdentity(
            id = identity.id.value,
            source_id = identity.sourceId.value,
            remote_book_id = identity.remoteBookId?.value,
            remote_file_id = identity.remoteFileId?.value,
            file_hash = identity.fileHash,
            file_size = identity.fileSize,
            epub_identifier = identity.epubIdentifier,
            title_normalized = identity.titleNormalized,
            author_normalized = identity.authorNormalized,
            created_at = identity.createdAtEpochMillis,
            updated_at = identity.updatedAtEpochMillis,
        )
    }

    private fun updateBookIdentity(identity: LocalBookIdentity) {
        database.storageQueries.updateBookIdentity(
            source_id = identity.sourceId.value,
            remote_book_id = identity.remoteBookId?.value,
            remote_file_id = identity.remoteFileId?.value,
            file_hash = identity.fileHash,
            file_size = identity.fileSize,
            epub_identifier = identity.epubIdentifier,
            title_normalized = identity.titleNormalized,
            author_normalized = identity.authorNormalized,
            updated_at = identity.updatedAtEpochMillis,
            id = identity.id.value,
        )
    }

    private fun insertPublicationFile(file: LocalPublicationFile) {
        database.storageQueries.insertPublicationFile(
            id = file.id.value,
            book_identity_id = file.bookIdentityId.value,
            source_id = file.sourceId.value,
            remote_file_id = file.remoteFileId.value,
            local_path = file.localPath,
            format = file.format.toPublicationFileStorageValue(),
            file_size = file.fileSize,
            file_hash = file.fileHash,
            downloaded_at = file.downloadedAtEpochMillis,
            last_opened_at = file.lastOpenedAtEpochMillis,
        )
    }

    private fun updatePublicationFile(file: LocalPublicationFile) {
        database.storageQueries.updatePublicationFile(
            book_identity_id = file.bookIdentityId.value,
            source_id = file.sourceId.value,
            remote_file_id = file.remoteFileId.value,
            local_path = file.localPath,
            format = file.format.toPublicationFileStorageValue(),
            file_size = file.fileSize,
            file_hash = file.fileHash,
            downloaded_at = file.downloadedAtEpochMillis,
            last_opened_at = file.lastOpenedAtEpochMillis,
            id = file.id.value,
        )
    }

    private fun mapBookIdentity(
        id: String,
        sourceId: String,
        remoteBookId: String?,
        remoteFileId: String?,
        fileHash: String?,
        fileSize: Long?,
        epubIdentifier: String?,
        titleNormalized: String?,
        authorNormalized: String?,
        createdAt: Long,
        updatedAt: Long,
    ): LocalBookIdentity =
        LocalBookIdentity(
            id = BookIdentityId(id),
            sourceId = SourceId(sourceId),
            remoteBookId = remoteBookId?.let(::RemoteBookId),
            remoteFileId = remoteFileId?.let(::RemoteFileId),
            fileHash = fileHash,
            fileSize = fileSize,
            epubIdentifier = epubIdentifier,
            titleNormalized = titleNormalized,
            authorNormalized = authorNormalized,
            createdAtEpochMillis = createdAt,
            updatedAtEpochMillis = updatedAt,
        )

    private fun mapPublicationFile(
        id: String,
        bookIdentityId: String,
        sourceId: String,
        remoteFileId: String?,
        localPath: String,
        format: String,
        fileSize: Long?,
        fileHash: String?,
        downloadedAt: Long,
        lastOpenedAt: Long?,
    ): LocalPublicationFile =
        LocalPublicationFile(
            id = PublicationFileId(id),
            bookIdentityId = BookIdentityId(bookIdentityId),
            sourceId = SourceId(sourceId),
            remoteFileId = RemoteFileId(remoteFileId ?: id),
            localPath = localPath,
            format = format.toPublicationFileFormat(),
            fileSize = fileSize,
            fileHash = fileHash,
            downloadedAtEpochMillis = downloadedAt,
            lastOpenedAtEpochMillis = lastOpenedAt,
        )
}

private fun PublicationFormat.toPublicationFileStorageValue(): String = name

private fun String.toPublicationFileFormat(): PublicationFormat =
    PublicationFormat.entries.firstOrNull { it.name == uppercase() } ?: PublicationFormat.UNKNOWN
