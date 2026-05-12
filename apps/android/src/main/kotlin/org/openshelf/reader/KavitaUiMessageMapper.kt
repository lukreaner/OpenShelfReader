package org.openshelf.reader

import kotlinx.coroutines.CancellationException
import org.openshelf.reader.download.DownloadPublicationFileError
import org.openshelf.reader.source.api.SourceAdapterException
import org.openshelf.reader.source.api.SourceError

internal fun messageForSourceError(
    error: SourceError,
    apiKey: String,
): String {
    val message = when (error) {
        SourceError.ApiKeyExpired -> "This Kavita API key has expired. Create a new key and try again."
        SourceError.NetworkUnavailable -> "Cannot reach the Kavita server. Check the URL and your network connection."
        SourceError.NotFound -> "That Kavita item was not found. Refresh the list and try again."
        SourceError.Unauthorized -> "The Kavita server rejected this API key. Check the key and try again."
        is SourceError.UnsupportedFormat -> "Only EPUB and PDF downloads are supported in this alpha."
        is SourceError.UnexpectedResponse -> "The Kavita server returned an unexpected response (HTTP ${error.status})."
        is SourceError.UnsupportedOperation -> "This Kavita operation is not supported in the app yet."
        is SourceError.UnsupportedServerVersion -> "This Kavita server version is not supported yet."
        is SourceError.Unknown -> "Something went wrong while talking to Kavita. Check the server URL and try again."
    }

    return message.redactApiKey(apiKey)
}

internal fun messageForThrowable(
    error: Throwable,
    apiKey: String,
): String {
    if (error is CancellationException) throw error

    val message = when (error) {
        is SourceAdapterException -> messageForSourceError(error.error, apiKey)
        is IllegalArgumentException -> "Enter a valid HTTP or HTTPS Kavita server URL."
        else -> "Something went wrong while talking to Kavita. Check the server URL and try again."
    }

    return message.redactApiKey(apiKey)
}

internal fun messageForDownloadError(
    error: DownloadPublicationFileError,
    apiKey: String,
): String {
    val message = when (error) {
        is DownloadPublicationFileError.SizeMismatch -> "The download did not match the expected file size. Try again."
        is DownloadPublicationFileError.SourceFailure -> messageForSourceError(error.error, apiKey)
        is DownloadPublicationFileError.StorageFailure -> "The file could not be saved on this device."
        is DownloadPublicationFileError.UnsupportedFormat -> "Only EPUB and PDF downloads are supported in this alpha."
    }

    return message.redactApiKey(apiKey)
}

internal fun String.redactApiKey(apiKey: String): String {
    val secret = apiKey.trim()
    if (secret.isBlank()) return this
    return replace(secret, "<redacted>")
}
