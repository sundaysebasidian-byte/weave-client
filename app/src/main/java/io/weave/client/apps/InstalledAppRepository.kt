package io.weave.client.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Immutable

@Immutable
data class InstalledApp(
    val packageName: String,
    val label: String,
    val monogram: String,
    val tint: Long,
    val migrationCandidate: Boolean = false,
)

/**
 * Lists only apps that advertise a launcher activity.
 *
 * This intentionally avoids QUERY_ALL_PACKAGES. It is enough for a user-driven app picker and
 * respects Android 11+ package visibility and Google Play's sensitive-permission policy.
 */
class InstalledAppRepository(
    private val context: Context,
) {
    fun listLaunchableApps(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, 0)
        }

        val launchable = resolved.asSequence()
            .mapNotNull { info ->
                val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                if (packageName == context.packageName) return@mapNotNull null
                val label = info.loadLabel(context.packageManager).toString().trim()
                    .ifEmpty { packageName.substringAfterLast('.') }
                InstalledApp(
                    packageName = packageName,
                    label = label,
                    monogram = label.monogram(),
                    tint = APP_TINTS[(packageName.hashCode() and Int.MAX_VALUE) % APP_TINTS.size],
                    migrationCandidate = ProxyClientDetector.matches(packageName, label),
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
        // Exact package queries also find known clients whose launcher entry is hidden/disabled.
        // Never enumerate all packages or open another app's private configuration.
        val known = ProxyClientDetector.knownPackages.mapNotNull { packageName ->
            try {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getApplicationInfo(packageName, 0)
                val label = context.packageManager.getApplicationLabel(info).toString()
                InstalledApp(packageName, label, label.monogram(), APP_TINTS[0], true)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            } catch (_: SecurityException) {
                null
            }
        }
        return (launchable + known).distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    private fun String.monogram(): String {
        val codePoints = codePoints().limit(2).toArray()
        return codePoints.joinToString(separator = "") { String(Character.toChars(it)) }
            .uppercase()
    }

    private companion object {
        val APP_TINTS = longArrayOf(
            0xFFFFE4E1,
            0xFFE3F0FF,
            0xFFE2F7E9,
            0xFFEAE7FF,
            0xFFFFF0D6,
            0xFFE1F4F5,
        )
    }
}

/** Exact identifiers only: a generic VPN-looking label must never become a migration prompt. */
internal object ProxyClientDetector {
    val knownPackages = setOf(
        "com.github.kr328.clash",
        "com.github.kr328.clash.premium",
        "com.github.metacubex.clash.meta",
        "com.github.metacubex.clash",
        "com.github.metacubex.clash.alpha",
        "com.nebula.karing",
        "com.nebula.clashmi",
        "com.v2ray.ang",
        "io.nekohasekai.sagernet",
        "io.nekohasekai.sfa",
        "moe.nb4a",
        "app.hiddify.com",
        "com.follow.clash",
        "com.follow.clash.dev",
    )
    private val knownLabels = setOf(
        "clash meta",
        "clash meta for android",
        "clash meta alpha",
        "clash for android",
        "cmfa",
        "flclash",
        "hiddify",
        "karing",
        "nekobox",
        "sagernet",
        "sing-box",
        "sing-box for android",
        "v2rayng",
        "clash mi",
        "clashmi",
        "clash verge",
        "clash verge rev",
        "clash for windows",
        "clashx",
    )

    fun matches(packageName: String, label: String): Boolean {
        if (packageName.trim().lowercase(java.util.Locale.ROOT) in knownPackages) return true
        return label.trim().replace(Regex("\\s+"), " ").lowercase(java.util.Locale.ROOT) in knownLabels
    }
}
