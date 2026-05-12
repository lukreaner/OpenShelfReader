package org.openshelf.reader.credentials

import org.openshelf.reader.source.api.SourceId

internal interface SecureApiKeyStore {
    suspend fun load(sourceId: SourceId): String?

    suspend fun save(sourceId: SourceId, apiKey: String)

    suspend fun delete(sourceId: SourceId)
}

internal class SecureApiKeyStoreException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
