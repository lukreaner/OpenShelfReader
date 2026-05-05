package org.openshelf.reader.storage

interface ReadingPositionRepository {
    suspend fun upsert(position: LocalReadingPosition)

    suspend fun getLatestForBook(bookIdentityId: BookIdentityId): LocalReadingPosition?

    suspend fun getById(positionId: ReadingPositionId): LocalReadingPosition?
}
