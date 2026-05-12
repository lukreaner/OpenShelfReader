package org.openshelf.reader.storage

import org.openshelf.reader.source.api.SourceId
import org.openshelf.reader.source.api.SourceType

interface SourceAccountRepository {
    suspend fun upsert(account: SavedSourceAccount)

    suspend fun getById(id: SourceId): SavedSourceAccount?

    suspend fun getLatestByType(type: SourceType): SavedSourceAccount?

    suspend fun delete(id: SourceId)
}
