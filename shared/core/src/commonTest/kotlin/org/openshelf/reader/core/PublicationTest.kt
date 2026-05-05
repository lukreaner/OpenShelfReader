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
    fun rejectsBlankPublicationIds() {
        assertFailsWith<IllegalArgumentException> {
            PublicationId(" ")
        }
    }
}
