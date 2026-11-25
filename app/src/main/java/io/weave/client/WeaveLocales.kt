package io.weave.client

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import io.weave.client.data.RuntimeSettingsStore
import io.weave.client.domain.WeaveLanguage
import java.util.Locale

/**
 * App language handling. On Android 13+ the system per-app language setting is the source of
 * truth (Settings › Apps › Weave › Language and the in-app picker stay in sync). Older releases
 * keep the choice in RuntimeSettingsStore and wrap resource contexts explicitly.
 */
object WeaveLocales {
    fun locale(language: WeaveLanguage): Locale = Locale.forLanguageTag(language.localeTag)

    /** Resources context for system surfaces (notifications, tiles) in the app language. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val language = RuntimeSettingsStore(base).explicitLanguage() ?: return base
        return wrap(base, language)
    }

    /** Resource context after the profile has loaded; never reads or mutates storage. */
    fun wrap(base: Context, language: WeaveLanguage): Context {
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale(language))
        return base.createConfigurationContext(configuration)
    }

    /** Mirrors the system per-app language into the store before the UI reads it. */
    fun syncFromSystem(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val store = RuntimeSettingsStore(context)
        val manager = context.getSystemService(LocaleManager::class.java) ?: return
        if (!store.isLocaleMigrated()) {
            // An explicit choice not supported by this binary belongs to Android, not to
            // legacy migration. Keep leaving it untouched until the explicit choice changes.
            val beforeMigration = manager.applicationLocales
            if (!beforeMigration.isEmpty && WeaveLanguage.matchSystem(beforeMigration[0]) == null) return
            // Versions before 0.4 kept the in-app choice only in the store. Hand it to the system
            // once instead of treating the empty system setting as "follow the device".
            if (migrateLegacyAppLanguage(
                    language = store.explicitLanguage(),
                    applyToSystem = { manager.applicationLocales = LocaleList.forLanguageTags(it.localeTag) },
                    markMigrated = store::markLocaleMigrated,
                )
            ) return
        }
        // Keep the legacy post-migration observation when there was no selection to hand off.
        val locales = manager.applicationLocales
        val selected = if (locales.isEmpty) {
            null
        } else {
            WeaveLanguage.matchSystem(locales[0]) ?: return
        }
        if (selected != store.explicitLanguage()) {
            if (selected == null) store.setFollowSystemLanguage() else store.setLanguage(selected)
        }
    }

    /** Null follows the device language. */
    fun apply(context: Context, language: WeaveLanguage?) {
        val store = RuntimeSettingsStore(context)
        if (language == null) store.setFollowSystemLanguage() else store.setLanguage(language)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                language?.let { LocaleList.forLanguageTags(it.localeTag) } ?: LocaleList.getEmptyLocaleList()
        }
    }
}

/** Keep the stored choice eligible for retry until Android has accepted it. */
internal fun migrateLegacyAppLanguage(
    language: WeaveLanguage?,
    applyToSystem: (WeaveLanguage) -> Unit,
    markMigrated: () -> Unit,
): Boolean {
    if (language != null) applyToSystem(language)
    markMigrated()
    return language != null
}
