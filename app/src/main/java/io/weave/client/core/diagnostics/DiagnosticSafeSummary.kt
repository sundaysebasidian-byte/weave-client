package io.weave.client.core.diagnostics

import io.weave.client.core.ipquality.IpQualityReport

/** A structural allowlist, not regex redaction of secrets or free-form error messages. */
object DiagnosticSafeSummary {
    fun build(
        version: String,
        local: PrivacyObservationReport,
        ip: IpQualityReport?,
        sites: CommonEndpointReport?,
        historical: Boolean,
        browserMeasured: Boolean,
        browserCandidates: Int,
    ): String = buildString {
        appendLine("Weave Android · diagnostic summary v1")
        appendLine("app=" + (version.takeIf { it.matches(Regex("[A-Za-z0-9.+-]{1,48}")) } ?: "unknown"))
        appendLine("historical=$historical")
        appendLine("local_verified=${local.verifiedCount}; local_attention=${local.attentionCount}")
        appendLine("ip_measured=${ip != null}; ipv4_present=${!ip?.ipv4.isNullOrBlank()}; ipv6_present=${!ip?.ipv6.isNullOrBlank()}")
        appendLine("browser_measured=$browserMeasured; ice_candidates=${browserCandidates.coerceIn(0, 999)}")
        CommonEndpointProbe.COMMON_ENDPOINTS.forEach { trusted ->
            val row = sites?.results?.firstOrNull { it.endpoint.id == trusted.id } ?: return@forEach
            append(trusted.id).append(": ").append(row.state.name)
            append("; http=").append(row.statusCode?.takeIf { it in 100..599 } ?: "unknown")
            append("; ms=").append(row.latencyMs?.takeIf { it in 1..60_000 } ?: "unknown")
            append("; failure=").append(row.failure?.name ?: "none").appendLine()
        }
        append("Snapshot only. No IP addresses, node/subscription names, credentials, fingerprints or raw errors. Not proof of leak-free networking.")
    }
}
