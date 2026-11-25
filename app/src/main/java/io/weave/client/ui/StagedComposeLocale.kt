package io.weave.client.ui

/**
 * Renderer for verification of not-yet-advertised locales. A caller must load a complete
 * catalog first. Recognized copy has no English fallback; unknown input (including user data)
 * is still passed through verbatim. No file I/O runs during text rendering.
 */
internal class StagedComposeLocale(
    val languageTag: String,
    private val entries: Map<String, String>,
) {
    fun lookupStatic(key: String): String? = if (key.startsWith("dynamic:")) null else entries[key]

    fun static(key: String): String = entries.getValue(key)

    fun format(key: String, vararg arguments: Pair<String, String>): String {
        val template = entries.getValue("dynamic:$key")
        // Released templates have at most seven distinct slots. Avoid a short-lived map
        // for these calls; keep a map for larger argument lists to bound repeated lookup.
        val values = if (arguments.size > 8) arguments.toMap() else null
        // Match only placeholders in the template. Inserted values are never scanned again.
        return NAMED_SLOT.replace(template) { match ->
            val slot = match.groupValues[1]
            if (values != null) values.getValue(slot)
            else arguments.lastOrNull { it.first == slot }?.second
                ?: throw NoSuchElementException("Key $slot is missing in the map.")
        }
    }

    private companion object {
        val NAMED_SLOT = Regex("""[{]([A-Za-z_][A-Za-z0-9_]*)[}]""")
    }
}
