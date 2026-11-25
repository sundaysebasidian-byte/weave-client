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
        val values = arguments.toMap()
        // Match only placeholders in the template. A node name containing "{count}" or "v1.2"
        // is never scanned or formatted again when inserted.
        return NAMED_SLOT.replace(template) { values.getValue(it.groupValues[1]) }
    }

    private companion object {
        val NAMED_SLOT = Regex("""[{]([A-Za-z_][A-Za-z0-9_]*)[}]""")
    }
}
