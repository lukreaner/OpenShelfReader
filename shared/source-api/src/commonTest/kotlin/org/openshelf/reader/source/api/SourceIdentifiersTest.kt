package org.openshelf.reader.source.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceIdentifiersTest {
    @Test
    fun idsPreserveValues() {
        assertEquals("source", SourceId("source").value)
        assertEquals("library", RemoteLibraryId("library").value)
        assertEquals("series", RemoteSeriesId("series").value)
        assertEquals("book", RemoteBookId("book").value)
        assertEquals("file", RemoteFileId("file").value)
    }

    @Test
    fun idsRejectBlankValues() {
        assertFailsWith<IllegalArgumentException> { SourceId(" ") }
        assertFailsWith<IllegalArgumentException> { RemoteLibraryId(" ") }
        assertFailsWith<IllegalArgumentException> { RemoteSeriesId(" ") }
        assertFailsWith<IllegalArgumentException> { RemoteBookId(" ") }
        assertFailsWith<IllegalArgumentException> { RemoteFileId(" ") }
    }
}
