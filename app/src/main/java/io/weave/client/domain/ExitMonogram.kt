package io.weave.client.domain

/**
 * A two-letter region code for the exit monogram on Home, read from the node name the user's
 * subscription provides. Flag emoji win, then well-known region names; anything else has no
 * monogram and Home shows a route glyph instead of guessing. The keywords are matching data, not
 * UI copy.
 */
object ExitMonogram {
    fun of(name: String): String? {
        val codePoints = name.codePoints().toArray()
        for (i in 0 until codePoints.size - 1) {
            val a = codePoints[i]
            val b = codePoints[i + 1]
            if (a in REGIONAL_A..REGIONAL_Z && b in REGIONAL_A..REGIONAL_Z) {
                return "${'A' + (a - REGIONAL_A)}${'A' + (b - REGIONAL_A)}"
            }
        }
        val lower = name.lowercase()
        return regions.firstOrNull { (_, keys) -> keys.any { it.containsMatchIn(lower) } }?.first
    }

    private const val REGIONAL_A = 0x1F1E6
    private const val REGIONAL_Z = 0x1F1FF

    /** Latin keywords match whole words only, so "usa" never matches inside "jerusalem". */
    private fun key(word: String): Regex =
        if (word.all { it.code < 0x80 }) Regex("(?<![a-z])${Regex.escape(word)}(?![a-z])") else Regex(Regex.escape(word))

    private val regions: List<Pair<String, List<Regex>>> = listOf(
        "HK" to listOf("香港", "hong kong", "hongkong"),
        "TW" to listOf("台湾", "臺灣", "台灣", "taiwan"),
        "JP" to listOf("日本", "japan", "tokyo", "东京", "osaka", "大阪"),
        "SG" to listOf("新加坡", "singapore"),
        "US" to listOf("美国", "美國", "united states", "usa", "los angeles", "洛杉矶", "san jose", "seattle"),
        "KR" to listOf("韩国", "韓國", "korea", "首尔", "seoul"),
        "GB" to listOf("英国", "英國", "united kingdom", "london", "伦敦"),
        "DE" to listOf("德国", "德國", "germany", "frankfurt", "法兰克福"),
        "FR" to listOf("法国", "法國", "france", "paris"),
        "NL" to listOf("荷兰", "荷蘭", "netherlands", "amsterdam"),
        "CA" to listOf("加拿大", "canada"),
        "AU" to listOf("澳大利亚", "澳洲", "australia", "sydney"),
        "IN" to listOf("印度", "india"),
        "MO" to listOf("澳门", "澳門", "macau", "macao"),
    ).map { (code, words) -> code to words.map(::key) }
}
