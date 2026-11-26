package io.weave.client.ui

import io.weave.client.core.engine.NodeHealthSnapshot
import io.weave.client.domain.WeaveLanguage

internal fun probeResultText(
    attempts: Int,
    successes: Int,
    language: WeaveLanguage,
    staged: StagedComposeLocale? = null,
    released: ReleasedAppCopy? = null,
): String = probeResultText(attempts, successes) { resolveAppOwnedCopy(it, language, staged, released) }

private inline fun probeResultText(attempts: Int, successes: Int, copy: (String) -> String): String {
    val label = copy("探测失败率")
    if (attempts <= 0) return "$label —"
    val validSuccesses = successes.coerceIn(0, attempts)
    val failure = (attempts - validSuccesses) * 100 / attempts
    return "$label $failure% · ${copy("成功")} $validSuccesses/$attempts"
}

internal fun nodeProbeResultText(
    health: NodeHealthSnapshot?,
    checked: Boolean,
    language: WeaveLanguage,
    staged: StagedComposeLocale? = null,
    released: ReleasedAppCopy? = null,
): String {
    fun copy(source: String) = resolveAppOwnedCopy(source, language, staged, released)
    val latency = health?.latencyMs?.let { "$it ms" }
        ?: copy(if (checked && health != null) "超时" else "未检测")
    // Core history is not an active sample series: do not manufacture 0% from one cached delay.
    return if (checked && health != null) {
        latency + "\n" + probeResultText(health.samples, health.successfulSamples, ::copy)
    } else latency
}
