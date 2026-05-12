package org.openshelf.reader.reader

import org.openshelf.reader.core.PublicationFormat
import org.openshelf.reader.storage.BookIdentityId
import org.openshelf.reader.storage.DeviceId
import org.openshelf.reader.storage.LocalReadingPosition
import org.openshelf.reader.storage.ReadingPositionId
import org.openshelf.reader.storage.ReadingPositionSource
import org.openshelf.reader.storage.ReadingSessionId
import org.readium.r2.shared.publication.Locator

internal class ReaderPositionMapper(
    private val clock: () -> Long,
) {
    fun toLocalReadingPosition(
        locator: Locator,
        bookIdentityId: BookIdentityId,
        deviceId: DeviceId,
        sessionId: ReadingSessionId,
    ): LocalReadingPosition =
        toLocalReadingPosition(
            snapshot = locator.toReaderLocatorSnapshot(),
            bookIdentityId = bookIdentityId,
            deviceId = deviceId,
            sessionId = sessionId,
        )

    fun toLocalReadingPosition(
        snapshot: ReaderLocatorSnapshot,
        bookIdentityId: BookIdentityId,
        deviceId: DeviceId,
        sessionId: ReadingSessionId,
    ): LocalReadingPosition {
        return LocalReadingPosition(
            id = ReadingPositionId("reader-position:${bookIdentityId.value}"),
            bookIdentityId = bookIdentityId,
            format = PublicationFormat.EPUB,
            locatorJson = snapshot.locatorJson,
            progression = snapshot.totalProgression,
            chapterHref = snapshot.href,
            chapterProgression = snapshot.chapterProgression,
            source = ReadingPositionSource.READER,
            deviceId = deviceId,
            sessionId = sessionId,
            updatedAtEpochMillis = clock(),
            finished = snapshot.totalProgression?.let { it >= FinishedProgressionThreshold } == true,
        )
    }

    private companion object {
        const val FinishedProgressionThreshold = 0.995
    }
}

internal data class ReaderLocatorSnapshot(
    val locatorJson: String,
    val totalProgression: Double?,
    val href: String?,
    val chapterProgression: Double?,
)

private fun Locator.toReaderLocatorSnapshot(): ReaderLocatorSnapshot =
    ReaderLocatorSnapshot(
        locatorJson = toJSON().toString(),
        totalProgression = locations.totalProgression,
        href = href.toString(),
        chapterProgression = locations.progression,
    )
