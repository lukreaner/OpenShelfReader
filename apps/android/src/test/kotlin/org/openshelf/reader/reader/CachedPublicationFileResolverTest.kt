package org.openshelf.reader.reader

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CachedPublicationFileResolverTest {
    @Test
    fun resolvesExistingRelativeEpubInsideRoot() {
        val root = Files.createTempDirectory("openshelf-cache")
        val epub = root.resolve("publication-cache/source/book/file.epub")
        Files.createDirectories(epub.parent)
        Files.write(epub, byteArrayOf(1, 2, 3))

        val result = CachedPublicationFileResolver(root.toFile())
            .resolveExistingEpub("publication-cache/source/book/file.epub")

        val resolved = assertIs<CachedPublicationFileResolution.Resolved>(result)
        assertEquals(epub.toFile().canonicalFile, resolved.file)
    }

    @Test
    fun rejectsBlankAbsoluteAndTraversalPaths() {
        val resolver = CachedPublicationFileResolver(Files.createTempDirectory("openshelf-cache").toFile())

        assertIs<CachedPublicationFileResolution.InvalidPath>(resolver.resolveExistingEpub(""))
        assertIs<CachedPublicationFileResolution.InvalidPath>(resolver.resolveExistingEpub("/tmp/file.epub"))
        assertIs<CachedPublicationFileResolution.InvalidPath>(
            resolver.resolveExistingEpub("publication-cache/../file.epub"),
        )
        assertIs<CachedPublicationFileResolution.InvalidPath>(
            resolver.resolveExistingEpub("publication-cache\\..\\file.epub"),
        )
    }

    @Test
    fun reportsMissingEpubWithReadableMessage() {
        val resolver = CachedPublicationFileResolver(Files.createTempDirectory("openshelf-cache").toFile())

        val result = resolver.resolveExistingEpub("publication-cache/source/book/missing.epub")

        val missing = assertIs<CachedPublicationFileResolution.Missing>(result)
        assertTrue(missing.message.contains("no longer available"))
    }

    @Test
    fun rejectsNonEpubFile() {
        val root = Files.createTempDirectory("openshelf-cache")
        val pdf = root.resolve("publication-cache/source/book/file.pdf")
        Files.createDirectories(pdf.parent)
        Files.write(pdf, byteArrayOf(1, 2, 3))

        val result = CachedPublicationFileResolver(root.toFile())
            .resolveExistingEpub("publication-cache/source/book/file.pdf")

        assertIs<CachedPublicationFileResolution.UnsupportedFormat>(result)
    }
}
