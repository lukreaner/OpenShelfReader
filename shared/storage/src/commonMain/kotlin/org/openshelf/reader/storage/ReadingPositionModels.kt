package org.openshelf.reader.storage

import org.openshelf.reader.core.PublicationFormat

@JvmInline
value class BookIdentityId(val value: String) {
    init {
        require(value.isNotBlank()) { "Book identity id must not be blank." }
    }
}

@JvmInline
value class ReadingPositionId(val value: String) {
    init {
        require(value.isNotBlank()) { "Reading position id must not be blank." }
    }
}

@JvmInline
value class DeviceId(val value: String) {
    init {
        require(value.isNotBlank()) { "Device id must not be blank." }
    }
}

@JvmInline
value class ReadingSessionId(val value: String) {
    init {
        require(value.isNotBlank()) { "Reading session id must not be blank." }
    }
}

enum class ReadingPositionSource {
    READER,
    TTS,
    REMOTE_SEED,
    IMPORTED,
    UNKNOWN,
}

data class LocalReadingPosition(
    val id: ReadingPositionId,
    val bookIdentityId: BookIdentityId,
    val format: PublicationFormat,
    val locatorJson: String,
    val progression: Double? = null,
    val chapterHref: String? = null,
    val chapterIndex: Int? = null,
    val chapterProgression: Double? = null,
    val pdfPageIndex: Int? = null,
    val pdfPageOffset: Double? = null,
    val source: ReadingPositionSource,
    val deviceId: DeviceId,
    val sessionId: ReadingSessionId,
    val updatedAtEpochMillis: Long,
    val finished: Boolean = false,
) {
    init {
        require(locatorJson.isNotBlank()) { "Locator JSON must not be blank." }
        require(progression == null || progression in 0.0..1.0) {
            "Progression must be between 0.0 and 1.0."
        }
        require(chapterIndex == null || chapterIndex >= 0) { "Chapter index must not be negative." }
        require(chapterProgression == null || chapterProgression in 0.0..1.0) {
            "Chapter progression must be between 0.0 and 1.0."
        }
        require(pdfPageIndex == null || pdfPageIndex >= 0) { "PDF page index must not be negative." }
        require(pdfPageOffset == null || pdfPageOffset in 0.0..1.0) {
            "PDF page offset must be between 0.0 and 1.0."
        }
        require(updatedAtEpochMillis >= 0) { "Reading position timestamp must not be negative." }
    }
}
