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
                STRING_LITERAL.findAll(file.readText()).map { it.groupValues[1] }.asIterable()
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

    private companion object {
        val STRING_LITERAL = Regex("\"((?:\\\\.|[^\"\\\\])*)\"")
    }
}
