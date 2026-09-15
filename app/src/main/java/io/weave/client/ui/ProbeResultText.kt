package io.weave.client.ui

import io.weave.client.core.engine.NodeHealthSnapshot
import io.weave.client.domain.WeaveLanguage

internal fun probeResultText(attempts: Int, successes: Int, language: WeaveLanguage): String {
    val label = localizeWeaveText("探测失败率", language)
    if (attempts <= 0) return "$label —"
    val validSuccesses = successes.coerceIn(0, attempts)
    val failure = (attempts - validSuccesses) * 100 / attempts
    return "$label $failure% · ${localizeWeaveText("成功", language)} $validSuccesses/$attempts"
}

internal fun nodeProbeResultText(health: NodeHealthSnapshot?, checked: Boolean, language: WeaveLanguage): String {
    val latency = health?.latencyMs?.let { "$it ms" }
        ?: localizeWeaveText(if (checked && health != null) "超时" else "未检测", language)
    // Core history is not an active sample series: do not manufacture 0% from one cached delay.
    return if (checked && health != null) {
        latency + "\n" + probeResultText(health.samples, health.successfulSamples, language)
    } else latency
}
