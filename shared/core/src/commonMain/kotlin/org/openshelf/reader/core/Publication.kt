package org.openshelf.reader.core

object OpenShelfCore {
    val supportedFormats: Set<PublicationFormat> = setOf(PublicationFormat.EPUB, PublicationFormat.PDF)
}

data class PublicationId(val value: String) {
    init {
        require(value.isNotBlank()) { "Publication id must not be blank." }
    }
}

data class Publication(
    val id: PublicationId,
    val title: String,
    val authors: List<String> = emptyList(),
    val format: PublicationFormat,
) {
    init {
        require(title.isNotBlank()) { "Publication title must not be blank." }
    }
}

enum class PublicationFormat {
    EPUB,
    PDF,
    CBZ,
    CBR,
    MOBI,
    AZW3,
    UNKNOWN,
}
