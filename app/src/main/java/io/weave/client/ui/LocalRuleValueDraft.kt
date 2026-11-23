package io.weave.client.ui

import io.weave.client.routing.LocalRouteRuleValidator

internal class LocalRuleValueDraft(input: String = "") {
    // Keep one overflow character so a paste cannot silently submit its shorter prefix.
    val value = input.take(LocalRouteRuleValidator.MAX_VALUE_LENGTH + 1)
    val exceedsLimit = value.length > LocalRouteRuleValidator.MAX_VALUE_LENGTH
    val canAdd = !exceedsLimit && value.isNotBlank()
}
