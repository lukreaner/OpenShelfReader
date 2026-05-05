package org.openshelf.reader.storage

import app.cash.sqldelight.db.SqlDriver
import org.openshelf.reader.core.PublicationFormat

class SqlDelightReadingPositionRepository(
    private val database: OpenShelfDatabase,
) : ReadingPositionRepository {
    constructor(driver: SqlDriver) : this(OpenShelfDatabase(driver))

    override suspend fun upsert(position: LocalReadingPosition) {
        val existing = getById(position.id)
        if (existing != null && position.updatedAtEpochMillis < existing.updatedAtEpochMillis) {
            return
        }
        val positionToStore = if (existing?.finished == true && !position.finished) {
            position.copy(finished = true)
        } else {
            position
        }

        if (existing == null) {
            insert(positionToStore)
        } else {
            update(positionToStore)
        }
    }

    override suspend fun getLatestForBook(bookIdentityId: BookIdentityId): LocalReadingPosition? =
        database.storageQueries
            .selectLatestReadingPositionForBook(bookIdentityId.value, ::mapReadingPosition)
            .executeAsOneOrNull()

    override suspend fun getById(positionId: ReadingPositionId): LocalReadingPosition? =
        database.storageQueries
            .selectReadingPositionById(positionId.value, ::mapReadingPosition)
            .executeAsOneOrNull()

    private fun insert(position: LocalReadingPosition) {
        database.storageQueries.insertReadingPosition(
            position.id.value,
            position.bookIdentityId.value,
            position.format.toStorageValue(),
            position.locatorJson,
            position.progression,
            position.chapterHref,
            position.chapterIndex?.toLong(),
            position.chapterProgression,
            position.pdfPageIndex?.toLong(),
            position.pdfPageOffset,
            position.source.toStorageValue(),
            position.deviceId.value,
            position.sessionId.value,
            position.updatedAtEpochMillis,
            position.finished.toStorageValue(),
        )
    }

    private fun update(position: LocalReadingPosition) {
        database.storageQueries.updateReadingPosition(
            position.bookIdentityId.value,
            position.format.toStorageValue(),
            position.locatorJson,
            position.progression,
            position.chapterHref,
            position.chapterIndex?.toLong(),
            position.chapterProgression,
            position.pdfPageIndex?.toLong(),
            position.pdfPageOffset,
            position.source.toStorageValue(),
            position.deviceId.value,
            position.sessionId.value,
            position.updatedAtEpochMillis,
            position.finished.toStorageValue(),
            position.id.value,
        )
    }

    private fun mapReadingPosition(
        id: String,
        bookIdentityId: String,
        format: String,
        locatorJson: String,
        progression: Double?,
        chapterHref: String?,
        chapterIndex: Long?,
        chapterProgression: Double?,
        pdfPageIndex: Long?,
        pdfPageOffset: Double?,
        source: String,
        deviceId: String,
        sessionId: String,
        updatedAt: Long,
        finished: Long,
    ): LocalReadingPosition =
        LocalReadingPosition(
            id = ReadingPositionId(id),
            bookIdentityId = BookIdentityId(bookIdentityId),
            format = format.toPublicationFormat(),
            locatorJson = locatorJson,
            progression = progression,
            chapterHref = chapterHref,
            chapterIndex = chapterIndex?.toInt(),
            chapterProgression = chapterProgression,
            pdfPageIndex = pdfPageIndex?.toInt(),
            pdfPageOffset = pdfPageOffset,
            source = source.toReadingPositionSource(),
            deviceId = DeviceId(deviceId),
            sessionId = ReadingSessionId(sessionId),
            updatedAtEpochMillis = updatedAt,
            finished = finished != 0L,
        )
}

private fun PublicationFormat.toStorageValue(): String = name

private fun String.toPublicationFormat(): PublicationFormat =
    PublicationFormat.entries.firstOrNull { it.name == uppercase() } ?: PublicationFormat.UNKNOWN

private fun ReadingPositionSource.toStorageValue(): String = name

private fun String.toReadingPositionSource(): ReadingPositionSource =
    ReadingPositionSource.entries.firstOrNull { it.name == uppercase() } ?: ReadingPositionSource.UNKNOWN

private fun Boolean.toStorageValue(): Long = if (this) 1L else 0L
