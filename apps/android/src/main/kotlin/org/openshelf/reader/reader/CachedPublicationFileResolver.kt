package org.openshelf.reader.reader

import android.content.Context
import java.io.File

internal class CachedPublicationFileResolver(
    private val rootDirectory: File,
) {
    constructor(context: Context) : this(context.filesDir)

    fun resolveExistingEpub(relativePath: String): CachedPublicationFileResolution {
        val file = resolve(relativePath).getOrElse { error ->
            return CachedPublicationFileResolution.InvalidPath(error.message ?: "The cached file path is invalid.")
        }

        if (!file.name.endsWith(".epub", ignoreCase = true)) {
            return CachedPublicationFileResolution.UnsupportedFormat("Only cached EPUB files can be opened in this spike.")
        }

        if (!file.isFile) {
            return CachedPublicationFileResolution.Missing("The cached EPUB file is no longer available on this device.")
        }

        return CachedPublicationFileResolution.Resolved(file)
    }

    private fun resolve(relativePath: String): Result<File> = runCatching {
        val normalized = relativePath.replace('\\', '/')
        require(normalized.isNotBlank()) { "The cached file path is blank." }
        require(!File(normalized).isAbsolute && !normalized.startsWith("/")) {
            "The cached file path must be relative."
        }

        val segments = normalized.split("/")
        require(segments.none { it.isBlank() || it == "." || it == ".." }) {
            "The cached file path must stay inside app storage."
        }

        val root = rootDirectory.canonicalFile
        val target = File(root, normalized).canonicalFile
        val rootPath = root.path
        val targetPath = target.path
        require(targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)) {
            "The cached file path must stay inside app storage."
        }
        target
    }
}

internal sealed interface CachedPublicationFileResolution {
    val message: String?

    data class Resolved(val file: File) : CachedPublicationFileResolution {
        override val message: String? = null
    }

    data class InvalidPath(override val message: String) : CachedPublicationFileResolution
    data class Missing(override val message: String) : CachedPublicationFileResolution
    data class UnsupportedFormat(override val message: String) : CachedPublicationFileResolution
}
