package org.openshelf.reader.source.api

sealed interface SourceCredentials {
    data class ApiKey(
        val serverUrl: String,
        val apiKey: String,
    ) : SourceCredentials {
        init {
            require(serverUrl.isNotBlank()) { "Server URL must not be blank." }
            require(apiKey.isNotBlank()) { "API key must not be blank." }
        }

        override fun toString(): String = "ApiKey(serverUrl=$serverUrl, apiKey=<redacted>)"
    }
}
