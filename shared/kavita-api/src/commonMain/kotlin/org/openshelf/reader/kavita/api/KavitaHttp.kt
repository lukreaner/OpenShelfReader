package org.openshelf.reader.kavita.api

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText

internal const val KavitaApiKeyHeader = "x-api-key"
internal const val KavitaRedactedSecret = "<redacted>"

private const val MaxBodySnippetLength = 300

internal suspend fun HttpResponse.redactedBodySnippet(apiKey: String): String? {
    val body = runCatching { bodyAsText() }.getOrNull() ?: return null
    return body.redactKavitaApiKey(apiKey).toBodySnippet()
}

internal fun String.redactKavitaApiKey(apiKey: String): String {
    if (apiKey.isBlank()) return this
    return replace(apiKey, KavitaRedactedSecret)
}

internal fun String.toRedactedBodySnippet(apiKey: String): String? {
    return redactKavitaApiKey(apiKey).toBodySnippet()
}

private fun String.toBodySnippet(): String? {
    return take(MaxBodySnippetLength).ifBlank { null }
}
