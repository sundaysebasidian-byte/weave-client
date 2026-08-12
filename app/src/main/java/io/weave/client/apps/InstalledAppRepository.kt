package io.weave.client.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

data class InstalledApp(
    val packageName: String,
    val label: String,
    val monogram: String,
    val tint: Long,
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

        return resolved.asSequence()
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
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
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

