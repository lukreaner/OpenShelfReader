package org.openshelf.reader.source.api

sealed interface SourceError {
    data object NetworkUnavailable : SourceError
    data object Unauthorized : SourceError
    data object ApiKeyExpired : SourceError
    data object NotFound : SourceError
    data class UnsupportedOperation(val operation: String) : SourceError {
        init {
            require(operation.isNotBlank()) { "Unsupported source operation must not be blank." }
        }
    }

    data class UnsupportedServerVersion(val version: String?) : SourceError
    data class UnexpectedResponse(val status: Int, val bodySnippet: String?) : SourceError
    data class Unknown(val message: String?) : SourceError
}
