package io.weave.client.ui

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.staticCompositionLocalOf
import io.weave.client.data.RuntimeSettingsStore
import io.weave.client.data.StoredReleaseAppCopySelection
import io.weave.client.domain.WeaveLanguage
import java.util.Locale

/** One verified release copy/resource context for the activity's explicit Android locale. */
internal class PackagedAppLocale private constructor(
    private val locale: Locale,
    private val seal: PackagedReleaseCopyAsset.Seal,
    val copy: ReleasedAppCopy,
) {
    val tag: String get() = seal.tag
    val nativeLabel: String get() = seal.label

    fun wrap(base: Context): Context {
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        return base.createConfigurationContext(configuration)
    }

    companion object {
        // Called on IO during Activity startup, not inside composition or persisted preference sync.
        fun observe(context: Context): PackagedAppLocale? =
            PackagedReleaseCopyAsset.optionalObservation { readObservedLocale(context) }

        private fun readObservedLocale(context: Context): PackagedAppLocale? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                val selected = RuntimeSettingsStore(context).releaseAppCopySelection()
                    as? StoredReleaseAppCopySelection.Selected ?: return null
                return verified(context, Locale.forLanguageTag(selected.canonicalTag))
            }
            val manager = context.getSystemService(LocaleManager::class.java) ?: return null
            val explicit = manager.applicationLocales
            if (explicit.isEmpty) return null
            val locale = explicit[0]
            val candidate = verified(context, locale) ?: return null
            // Do not publish a catalog loaded for a choice that changed while storage was read.
            val current = manager.applicationLocales
            if (current.isEmpty || current[0] != locale) return null
            return candidate
        }

        /** Verify sealed bytes on IO; only a selected catalog needs to be decoded. */
        fun availableChoices(context: Context, active: PackagedAppLocale?): List<PackagedLocaleChoice> =
            PackagedReleaseCopyAsset.optionalObservation {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    context.getSystemService(LocaleManager::class.java) == null
                ) return@optionalObservation emptyList()
                val assets = try {
                    context.assets.list("weave-locales").orEmpty().toSet()
                } catch (_: java.io.IOException) {
                    return@optionalObservation emptyList()
                }
                PackagedReleaseCopyAsset.Seal.values().mapNotNull { seal ->
                    if ("${seal.tag}.jsonl" !in assets) return@mapNotNull null
                    val valid = active?.tag == seal.tag || PackagedReleaseCopyAsset.readIfPackaged(
                        seal.tag, { path -> context.assets.open(path) }, ReleaseCopyCatalog::verifySeal,
                    ) == true
                    if (valid) PackagedLocaleChoice(seal.tag, seal.label) else null
                }
            }.orEmpty()

        fun readChoice(context: Context, choice: PackagedLocaleChoice): PackagedAppLocale? =
            verified(context, Locale.forLanguageTag(choice.tag))

        private fun verified(context: Context, locale: Locale): PackagedAppLocale? {
            val tag = locale.toLanguageTag()
            val seal = PackagedReleaseCopyAsset.sealFor(tag) ?: return null
            val copy = PackagedReleaseCopyAsset.readIfPackaged(
                tag,
                { path -> context.assets.open(path) },
                { key, input -> ReleasedAppCopy.load(key, input) },
            ) ?: return null
            return PackagedAppLocale(locale, seal, copy)
        }
    }
}

internal data class PackagedLocaleChoice(val tag: String, val nativeLabel: String)

internal sealed interface PackagedLanguageTap {
    val tag: String

    data class Regional(val locale: PackagedAppLocale) : PackagedLanguageTap {
        override val tag: String get() = locale.tag
    }

    data class Legacy(val language: WeaveLanguage?) : PackagedLanguageTap {
        override val tag: String get() = language?.localeTag.orEmpty()
    }
}

internal enum class PackagedLanguageTapResult { APPLIED, NOT_APPLIED, UNCONFIRMED }

/** Only the dialog tap boundary calls apply; rechecks never write. */
internal object SystemPackagedLanguagePicker {
    fun apply(context: Context, choice: PackagedLanguageTap): PackagedLanguageTapResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return PackagedLanguageTapResult.NOT_APPLIED
        val manager = PackagedReleaseCopyAsset.optionalObservation {
            context.getSystemService(LocaleManager::class.java)
        } ?: return PackagedLanguageTapResult.NOT_APPLIED
        val result = PackagedReleaseCopyAsset.commitExplicitTap(
            choice.tag,
            { manager.applicationLocales.toLanguageTags() },
            { selected -> manager.applicationLocales = LocaleList.forLanguageTags(selected) },
        )
        return when (result) {
            PackagedReleaseCopyAsset.Tap.APPLIED -> PackagedLanguageTapResult.APPLIED
            PackagedReleaseCopyAsset.Tap.NOT_APPLIED -> PackagedLanguageTapResult.NOT_APPLIED
            else -> PackagedLanguageTapResult.UNCONFIRMED
        }
    }

    fun confirms(context: Context, choice: PackagedLanguageTap): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return PackagedReleaseCopyAsset.optionalObservation {
            context.getSystemService(LocaleManager::class.java)
                ?.applicationLocales?.toLanguageTags() == choice.tag
        } == true
    }
}

internal class PackagedLanguagePicker(
    val selected: PackagedAppLocale?,
    val loadChoices: suspend () -> List<PackagedLocaleChoice>,
    val loadChoice: suspend (PackagedLocaleChoice) -> PackagedAppLocale?,
    val applyTap: (PackagedLanguageTap) -> PackagedLanguageTapResult,
    val reconcile: suspend (PackagedLanguageTap) -> Boolean,
)

internal val LocalPackagedLanguagePicker = staticCompositionLocalOf<PackagedLanguagePicker?> { null }
