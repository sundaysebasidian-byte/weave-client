package io.weave.client.ui

import io.weave.client.core.diagnostics.RouteLensResult

internal sealed interface RouteLensSubmission {
    data class Invalid(val message: String) : RouteLensSubmission
    data class Explained(val result: RouteLensResult) : RouteLensSubmission
}

/** Each click replaces the previous answer or error; invalid drafts never run the evaluator. */
internal fun submitRouteLens(
    domain: String,
    port: String,
    evaluate: (Int) -> RouteLensResult,
): RouteLensSubmission {
    val parsedPort = port.toIntOrNull()
    return when {
        domain.isBlank() -> RouteLensSubmission.Invalid("请输入域名")
        parsedPort == null || parsedPort !in 1..65535 -> RouteLensSubmission.Invalid("端口必须为 1–65535")
        else -> RouteLensSubmission.Explained(evaluate(parsedPort))
    }
}
