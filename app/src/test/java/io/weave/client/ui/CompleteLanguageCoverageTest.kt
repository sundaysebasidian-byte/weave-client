package io.weave.client.ui

import io.weave.client.domain.WeaveLanguage
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CompleteLanguageCoverageTest {
    @Test
    fun `visible static copy has no simplified Chinese fallback in latin locales`() {
        val sourceStrings = visibleSourceFiles()
            .flatMap { file ->
                stringLiterals(file.readText()).asIterable()
            }
            .filter(::isAuditableChineseLiteral)
            .toSortedSet()

        assertTrue("language audit did not find UI source strings", sourceStrings.isNotEmpty())
        listOf(
            WeaveLanguage.ENGLISH,
            WeaveLanguage.FRENCH,
            WeaveLanguage.GERMAN,
        ).forEach { language ->
            val remaining = sourceStrings.filter { source ->
                localizeWeaveText(source, language).any(::isHanCharacter)
            }
            assertTrue(
                "$language left ${remaining.size} Chinese UI strings:\n${remaining.take(20).joinToString("\n")}",
                remaining.isEmpty(),
            )
        }
    }

    private fun visibleSourceFiles(): List<File> = listOf(
        "src/main/java/io/weave/client/ui/WeaveApp.kt",
        "src/main/java/io/weave/client/ui/BrowserPrivacyLab.kt",
        "src/main/java/io/weave/client/ui/AppViewModel.kt",
        "src/main/java/io/weave/client/domain/Models.kt",
        "src/main/java/io/weave/client/core/diagnostics/PrivacyObservatory.kt",
        "src/main/java/io/weave/client/core/diagnostics/RouteLens.kt",
        "src/main/java/io/weave/client/core/ipquality/IpQualityProbe.kt",
    ).map(::resolveModuleFile).onEach { file ->
        assertTrue("missing language-audit input: $file", file.isFile)
    }

    private fun resolveModuleFile(path: String): File {
        val direct = File(path)
        if (direct.isFile) return direct
        return File("app", path)
    }

    private fun isAuditableChineseLiteral(value: String): Boolean =
        value.any(::isHanCharacter) &&
            '$' !in value &&
            '\\' !in value &&
            !value.startsWith(" · ") && // fragment localized after the complete status is built
            value !in WeaveLanguage.entries.map(WeaveLanguage::nativeLabel).toSet()

    private fun isHanCharacter(value: Char): Boolean =
        value.code in 0x3400..0x4DBF || value.code in 0x4E00..0x9FFF

    /**
     * Reads ordinary Kotlin string literals without a backtracking regex. The visible source
     * audit is run on CI's Java runtime too, where a large triple-quoted HTML literal can make
     * Pattern recurse deeply enough to throw StackOverflowError. Triple-quoted blocks are
     * implementation payloads rather than visible Compose copy, so they are skipped safely.
     */
    private fun stringLiterals(source: String): Sequence<String> = sequence {
        var index = 0
        while (index < source.length) {
            if (source.startsWith("\"\"\"", index)) {
                val end = source.indexOf("\"\"\"", index + 3)
                index = if (end >= 0) end + 3 else source.length
                continue
            }
            if (source[index] != '\"') {
                index += 1
                continue
            }

            index += 1
            val literal = StringBuilder()
            var closed = false
            while (index < source.length) {
                when (val character = source[index]) {
                    '\\' -> {
                        literal.append(character)
                        index += 1
                        if (index < source.length) {
                            literal.append(source[index])
                            index += 1
                        }
                    }
                    '\"' -> {
                        index += 1
                        closed = true
                        break
                    }
                    else -> {
                        literal.append(character)
                        index += 1
                    }
                }
            }
            if (closed) yield(literal.toString())
        }
    }
}
