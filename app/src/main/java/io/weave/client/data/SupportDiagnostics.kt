package io.weave.client.data

/** Allowlisted metadata only; no exception text, device ID, endpoints or subscription names. */
internal object SupportDiagnostics {
    fun build(version: String, sdk: Int, state: RecoveryState): String = buildString {
        appendLine("Weave Android diagnostics v1")
        appendLine("app=${version.takeIf { it.matches(Regex("[A-Za-z0-9.+-]{1,48}")) } ?: "unknown"}")
        appendLine("android_api=${sdk.coerceIn(1, 999)}")
        appendLine("safe_mode=${state.safeMode}")
        appendLine("consecutive_failures=${state.failureCount.coerceIn(0, 99)}")
        appendLine("has_previous_success=${state.lastHealthyAtMillis != null}")
        val code = Regex("(?i)\\bS\\d{2}(?::[A-Z]{2}\\d{1,3})?\\b")
            .find(state.lastFailure.orEmpty())?.value?.uppercase(java.util.Locale.ROOT)
        appendLine("failure_code=${code ?: "unavailable"}")
        append("Raw logs, addresses, credentials and subscription data are excluded.")
    }
}
