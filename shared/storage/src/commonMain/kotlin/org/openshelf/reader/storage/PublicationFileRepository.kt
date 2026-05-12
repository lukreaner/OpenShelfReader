package org.openshelf.reader.storage

import org.openshelf.reader.core.PublicationFormat
import org.openshelf.reader.source.api.RemoteFileId
import org.openshelf.reader.source.api.SourceId

interface PublicationFileRepository {
    suspend fun upsertBookIdentity(identity: LocalBookIdentity)

    suspend fun getBookIdentity(id: BookIdentityId): LocalBookIdentity?

    suspend fun upsertPublicationFile(file: LocalPublicationFile)

    suspend fun upsertCachedPublication(identity: LocalBookIdentity, file: LocalPublicationFile)

    suspend fun getPublicationFile(id: PublicationFileId): LocalPublicationFile?

    suspend fun findPublicationFile(
        sourceId: SourceId,
        remoteFileId: RemoteFileId,
        format: PublicationFormat,
    ): LocalPublicationFile?
}
