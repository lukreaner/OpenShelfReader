package org.openshelf.reader.source.api

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SourceCredentialsTest {
    @Test
    fun apiKeyCredentialsRedactSecretInToString() {
        val secret = "kavita-api-key-secret"
        val credentials = SourceCredentials.ApiKey(
            serverUrl = "https://library.example",
            apiKey = secret,
        )

        val printed = credentials.toString()

        assertFalse(printed.contains(secret))
        assertTrue(printed.contains("<redacted>"))
        assertTrue(printed.contains("https://library.example"))
    }

    @Test
    fun apiKeyCredentialsRejectBlankSecrets() {
        assertFailsWith<IllegalArgumentException> {
            SourceCredentials.ApiKey(
                serverUrl = "https://library.example",
                apiKey = " ",
            )
        }
    }
}
