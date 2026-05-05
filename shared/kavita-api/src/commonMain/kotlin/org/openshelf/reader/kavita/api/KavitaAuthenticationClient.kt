package org.openshelf.reader.kavita.api

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import org.openshelf.reader.source.api.SourceCredentials
import org.openshelf.reader.source.api.SourceError

class KavitaAuthenticationClient(
    private val httpClient: HttpClient,
) {
    suspend fun authenticate(credentials: SourceCredentials): KavitaAuthResult {
        val apiKeyCredentials = credentials as? SourceCredentials.ApiKey
            ?: return KavitaAuthResult.Failure(
                SourceError.Unknown("Kavita authentication requires API key credentials."),
            )

        val baseUrl = runCatching { KavitaBaseUrl.normalize(apiKeyCredentials.serverUrl) }
            .getOrNull()
            ?: return KavitaAuthResult.Failure(SourceError.Unknown("Invalid Kavita server URL."))

        return authenticate(baseUrl, apiKeyCredentials.apiKey)
    }

    private suspend fun authenticate(
        baseUrl: KavitaBaseUrl,
        apiKey: String,
    ): KavitaAuthResult {
        return try {
            val response = httpClient.get("${baseUrl.value}/api/plugin/authkey-expires") {
                header(KavitaApiKeyHeader, apiKey)
            }

            when (response.status) {
                HttpStatusCode.OK -> KavitaAuthResult.Success(
                    KavitaAuthStatus(
                        baseUrl = baseUrl,
                        authKeyExpiration = parseExpiration(response.bodyAsText()),
                    ),
                )

                HttpStatusCode.Unauthorized,
                HttpStatusCode.Forbidden,
                -> KavitaAuthResult.Failure(SourceError.Unauthorized)

                else -> KavitaAuthResult.Failure(
                    SourceError.UnexpectedResponse(
                        status = response.status.value,
                        bodySnippet = response.redactedBodySnippet(apiKey),
                    ),
                )
            }
        } catch (error: IOException) {
            KavitaAuthResult.Failure(SourceError.NetworkUnavailable)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            KavitaAuthResult.Failure(SourceError.Unknown("Kavita authentication failed unexpectedly."))
        }
    }

    private fun parseExpiration(body: String): KavitaAuthKeyExpiration {
        val trimmed = body.trim()
        if (trimmed == "null") return KavitaAuthKeyExpiration.DoesNotExpire
        if (trimmed.isBlank()) return KavitaAuthKeyExpiration.Unknown

        val json = runCatching { Json.parseToJsonElement(trimmed) }.getOrNull()
            ?: return KavitaAuthKeyExpiration.Unknown

        if (json is JsonNull) return KavitaAuthKeyExpiration.DoesNotExpire

        val expiresAt = runCatching { json.jsonObject["expiresAt"] }.getOrNull()
            ?: return KavitaAuthKeyExpiration.Unknown

        return when (expiresAt) {
            JsonNull -> KavitaAuthKeyExpiration.DoesNotExpire
            is JsonPrimitive -> expiresAt.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?.let(KavitaAuthKeyExpiration::ExpiresAt)
                ?: KavitaAuthKeyExpiration.Unknown

            is JsonObject -> KavitaAuthKeyExpiration.Unknown
            else -> KavitaAuthKeyExpiration.Unknown
        }
    }

    companion object {
        internal const val ApiKeyHeader = KavitaApiKeyHeader
    }
}
