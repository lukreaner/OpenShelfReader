package org.openshelf.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KavitaConnectionPolicyTest {
    private val policy = KavitaConnectionPolicy()

    @Test
    fun acceptsHttpsWithoutInsecureOptIn() {
        val result = policy.validate(
            rawServerUrl = "  https://library.example/kavita///  ",
            allowInsecureHttp = false,
        )

        val accepted = assertIs<KavitaConnectionPolicyResult.Accepted>(result)
        assertEquals("https://library.example/kavita", accepted.normalizedServerUrl)
        assertEquals(false, accepted.allowInsecureHttp)
    }

    @Test
    fun rejectsHttpWithoutInsecureOptIn() {
        val result = policy.validate(
            rawServerUrl = "http://library.local",
            allowInsecureHttp = false,
        )

        assertEquals(KavitaConnectionPolicyResult.InsecureHttpRequiresOptIn, result)
    }

    @Test
    fun acceptsHttpWithInsecureOptIn() {
        val result = policy.validate(
            rawServerUrl = "http://library.local/",
            allowInsecureHttp = true,
        )

        val accepted = assertIs<KavitaConnectionPolicyResult.Accepted>(result)
        assertEquals("http://library.local", accepted.normalizedServerUrl)
        assertEquals(true, accepted.allowInsecureHttp)
    }

    @Test
    fun rejectsInvalidUrls() {
        assertEquals(
            KavitaConnectionPolicyResult.InvalidUrl,
            policy.validate("library.example", allowInsecureHttp = true),
        )
        assertEquals(
            KavitaConnectionPolicyResult.InvalidUrl,
            policy.validate("https://library.example?apiKey=secret", allowInsecureHttp = true),
        )
        assertEquals(
            KavitaConnectionPolicyResult.InvalidUrl,
            policy.validate("ftp://library.example", allowInsecureHttp = true),
        )
    }
}
