package io.weave.client.data

import io.weave.client.core.vpn.RuntimeFailure

/** Allowlisted metadata only; no exception text, device ID, endpoints or subscription names. */
internal object SupportDiagnostics {
    private val runtimeCodes = RuntimeFailure.entries.mapTo(hashSetOf()) { it.code }

    fun build(version: String, sdk: Int, state: RecoveryState): String = buildString {
        appendLine("Weave Android diagnostics v1")
        appendLine("app=${version.takeIf { it.matches(Regex("[A-Za-z0-9.+-]{1,48}")) } ?: "unknown"}")
        appendLine("android_api=${sdk.coerceIn(1, 999)}")
        appendLine("safe_mode=${state.safeMode}")
        appendLine("consecutive_failures=${state.failureCount.coerceIn(0, 99)}")
        appendLine("has_previous_success=${state.lastHealthyAtMillis != null}")
        appendLine("failure_code=${SupportFailureCodes.project(state.lastFailure, runtimeCodes)}")
        append("Raw logs, addresses, credentials and subscription data are excluded.")
    }
}
