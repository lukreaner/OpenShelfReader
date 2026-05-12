package org.openshelf.reader

import org.openshelf.reader.kavita.api.KavitaBaseUrl

internal class KavitaConnectionPolicy {
    fun validate(
        rawServerUrl: String,
        allowInsecureHttp: Boolean,
    ): KavitaConnectionPolicyResult {
        val normalized = runCatching { KavitaBaseUrl.normalize(rawServerUrl).value }
            .getOrElse {
                return KavitaConnectionPolicyResult.InvalidUrl
            }

        if (normalized.startsWith("http://", ignoreCase = true) && !allowInsecureHttp) {
            return KavitaConnectionPolicyResult.InsecureHttpRequiresOptIn
        }

        return KavitaConnectionPolicyResult.Accepted(
            normalizedServerUrl = normalized,
            allowInsecureHttp = normalized.startsWith("http://", ignoreCase = true),
        )
    }
}

internal sealed interface KavitaConnectionPolicyResult {
    data class Accepted(
        val normalizedServerUrl: String,
        val allowInsecureHttp: Boolean,
    ) : KavitaConnectionPolicyResult

    data object InvalidUrl : KavitaConnectionPolicyResult
    data object InsecureHttpRequiresOptIn : KavitaConnectionPolicyResult
}
