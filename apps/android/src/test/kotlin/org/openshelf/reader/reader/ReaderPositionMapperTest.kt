package org.openshelf.reader.reader

import org.openshelf.reader.core.PublicationFormat
import org.openshelf.reader.storage.BookIdentityId
import org.openshelf.reader.storage.DeviceId
import org.openshelf.reader.storage.ReadingPositionSource
import org.openshelf.reader.storage.ReadingSessionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderPositionMapperTest {
    @Test
    fun mapsReadiumLocatorToLocalEpubPosition() {
        val snapshot = ReaderLocatorSnapshot(
            locatorJson = """{"href":"Text/chapter-1.xhtml","type":"application/xhtml+xml"}""",
            totalProgression = 0.42,
            href = "Text/chapter-1.xhtml",
            chapterProgression = 0.25,
        )

        val position = ReaderPositionMapper(clock = { 1234L }).toLocalReadingPosition(
            snapshot = snapshot,
            bookIdentityId = BookIdentityId("book-identity"),
            deviceId = DeviceId("device"),
            sessionId = ReadingSessionId("session"),
        )

        assertEquals("reader-position:book-identity", position.id.value)
        assertEquals(BookIdentityId("book-identity"), position.bookIdentityId)
        assertEquals(PublicationFormat.EPUB, position.format)
        assertEquals(0.42, position.progression)
        assertEquals("Text/chapter-1.xhtml", position.chapterHref)
        assertNull(position.chapterIndex)
        assertEquals(0.25, position.chapterProgression)
        assertNull(position.pdfPageIndex)
        assertNull(position.pdfPageOffset)
        assertEquals(ReadingPositionSource.READER, position.source)
        assertEquals(DeviceId("device"), position.deviceId)
        assertEquals(ReadingSessionId("session"), position.sessionId)
        assertEquals(1234L, position.updatedAtEpochMillis)
        assertFalse(position.finished)
        assertTrue(position.locatorJson.contains("Text/chapter-1.xhtml"))
    }

    @Test
    fun marksNearlyCompleteLocatorFinished() {
        val snapshot = ReaderLocatorSnapshot(
            locatorJson = """{"href":"Text/final.xhtml","type":"application/xhtml+xml"}""",
            totalProgression = 0.996,
            href = "Text/final.xhtml",
            chapterProgression = null,
        )

        val position = ReaderPositionMapper(clock = { 1234L }).toLocalReadingPosition(
            snapshot = snapshot,
            bookIdentityId = BookIdentityId("book-identity"),
            deviceId = DeviceId("device"),
            sessionId = ReadingSessionId("session"),
        )

        assertTrue(position.finished)
    }
}
