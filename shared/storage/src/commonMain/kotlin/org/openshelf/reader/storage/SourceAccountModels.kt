package org.openshelf.reader.storage

import org.openshelf.reader.source.api.SourceId
import org.openshelf.reader.source.api.SourceType

data class SavedSourceAccount(
    val id: SourceId,
    val type: SourceType,
    val displayName: String,
    val baseUrl: String,
    val allowInsecureHttp: Boolean = false,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(displayName.isNotBlank()) { "Source display name must not be blank." }
        require(baseUrl.isNotBlank()) { "Source base URL must not be blank." }
        require(createdAtEpochMillis >= 0) { "Created timestamp must not be negative." }
        require(updatedAtEpochMillis >= 0) { "Updated timestamp must not be negative." }
    }
}
