package org.openshelf.reader.source.api

@JvmInline
value class SourceId(val value: String) {
    init {
        require(value.isNotBlank()) { "Source id must not be blank." }
    }
}

@JvmInline
value class RemoteLibraryId(val value: String) {
    init {
        require(value.isNotBlank()) { "Remote library id must not be blank." }
    }
}

@JvmInline
value class RemoteSeriesId(val value: String) {
    init {
        require(value.isNotBlank()) { "Remote series id must not be blank." }
    }
}

@JvmInline
value class RemoteBookId(val value: String) {
    init {
        require(value.isNotBlank()) { "Remote book id must not be blank." }
    }
}

@JvmInline
value class RemoteFileId(val value: String) {
    init {
        require(value.isNotBlank()) { "Remote file id must not be blank." }
    }
}
