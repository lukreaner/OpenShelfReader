package org.openshelf.reader.source.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SourceCapabilitiesTest {
    @Test
    fun defaultsToNoDeclaredCapabilities() {
        val capabilities = SourceCapabilities()

        assertFalse(capabilities.supportsLibraryBrowsing)
        assertFalse(capabilities.supportsSearch)
        assertFalse(capabilities.supportsDownloads)
        assertFalse(capabilities.supportsRemoteProgressRead)
        assertFalse(capabilities.supportsRemoteProgressWrite)
        assertFalse(capabilities.supportsCollections)
        assertFalse(capabilities.supportsSeriesMetadata)
    }

    @Test
    fun usesValueEquality() {
        assertEquals(
            SourceCapabilities(
                supportsLibraryBrowsing = true,
                supportsSearch = true,
                supportsDownloads = true,
            ),
            SourceCapabilities(
                supportsLibraryBrowsing = true,
                supportsSearch = true,
                supportsDownloads = true,
            ),
        )
    }
}
