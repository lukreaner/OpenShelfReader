package org.openshelf.reader.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PublicationTest {
    @Test
    fun supportsMvpPublicationFormats() {
        assertEquals(
            setOf(PublicationFormat.EPUB, PublicationFormat.PDF),
            OpenShelfCore.supportedFormats,
        )
    }

    @Test
    fun knowsFuturePublicationFormats() {
        assertEquals(
            setOf(
                PublicationFormat.EPUB,
                PublicationFormat.PDF,
                PublicationFormat.CBZ,
                PublicationFormat.CBR,
                PublicationFormat.MOBI,
                PublicationFormat.AZW3,
                PublicationFormat.UNKNOWN,
            ),
            PublicationFormat.entries.toSet(),
        )
    }

    @Test
    fun rejectsBlankPublicationIds() {
        assertFailsWith<IllegalArgumentException> {
            PublicationId(" ")
        }
    }
}
