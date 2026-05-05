package org.openshelf.reader.kavita.api

import io.ktor.http.Url
import org.openshelf.reader.source.api.SourceError

@JvmInline
value class KavitaBaseUrl private constructor(val value: String) {
    override fun toString(): String = value

    companion object {
        fun normalize(rawUrl: String): KavitaBaseUrl {
            val trimmed = rawUrl.trim()
            require(trimmed.isNotBlank()) { "Kavita server URL must not be blank." }
            require("://" in trimmed) { "Kavita server URL must include an HTTP scheme." }
            require(!trimmed.contains('?') && !trimmed.contains('#')) {
                "Kavita server URL must not include a query or fragment."
            }

            val normalized = trimmed.trimEnd('/')
            val parsed = runCatching { Url(normalized) }
                .getOrElse { throw IllegalArgumentException("Invalid Kavita server URL.") }

            require(parsed.protocol.name == "http" || parsed.protocol.name == "https") {
                "Kavita server URL must use HTTP or HTTPS."
            }
            require(parsed.host.isNotBlank()) { "Kavita server URL must include a host." }

            return KavitaBaseUrl(normalized)
        }
    }
}

data class KavitaAuthStatus(
    val baseUrl: KavitaBaseUrl,
    val authKeyExpiration: KavitaAuthKeyExpiration,
)

sealed interface KavitaAuthKeyExpiration {
    data object DoesNotExpire : KavitaAuthKeyExpiration
    data object Unknown : KavitaAuthKeyExpiration

    data class ExpiresAt(val value: String) : KavitaAuthKeyExpiration {
        init {
            require(value.isNotBlank()) { "Kavita auth key expiration must not be blank." }
        }
    }
}

sealed interface KavitaAuthResult {
    data class Success(val status: KavitaAuthStatus) : KavitaAuthResult
    data class Failure(val error: SourceError) : KavitaAuthResult
}
