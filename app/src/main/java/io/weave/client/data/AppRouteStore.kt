package io.weave.client.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.weave.client.domain.AppRoute
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget

class AppRouteStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences get() = appContext.crossProcessPreferences(PREFERENCES_NAME)

    fun load(): List<AppRoute> = ProfileStorage.access(appContext) {
        readAll({ preferences })
    }

    internal fun snapshot(): List<AppRoute> = ProfileStorage.access(appContext) {
        readAll({ preferences }, strict = true)
    }

    fun save(routes: List<AppRoute>) = ProfileStorage.access(appContext) {
        writeAll({ preferences }, routes)
    }

    internal companion object {
        internal fun readAll(acquire: () -> SharedPreferences, strict: Boolean = false): List<AppRoute> {
            val preferences = acquire()
            val packages = preferences.getStringSet(KEY_PACKAGES, emptySet()).orEmpty()
            val ranked = ArrayList<NamedRoute>(packages.size)
            for (packageName in packages) {
                val route = read(preferences, packageName) ?: continue
                ranked.add(NamedRoute(route.appName.lowercase(), route))
            }
            ranked.sortBy { it.name }
            val records = ranked.map { it.route }
            if (strict) {
                check(records.mapTo(linkedSetOf()) { it.packageName } == packages) {
                    "应用分流无法完整读取，已停止备份"
                }
            }
            return records
        }

        private data class NamedRoute(val name: String, val route: AppRoute)

        private fun read(preferences: SharedPreferences, packageName: String): AppRoute? {
            if (!PACKAGE_NAME.matches(packageName)) return null
            val kind = preferences.getString(key(packageName, "kind"), null)
                ?.let { runCatching { RouteKind.valueOf(it) }.getOrNull() }
                ?: return null
            val subscriptionId = preferences.getString(key(packageName, "subscription"), null)
            val nodeId = preferences.getString(key(packageName, "node"), null)
            if (kind == RouteKind.AUTO && subscriptionId == null) return null
            if (kind == RouteKind.FIXED && (subscriptionId == null || nodeId == null)) return null
            val groupId = preferences.getString(key(packageName, "group"), null)
            if (kind == RouteKind.GROUP && groupId == null) return null

            return AppRoute(
                packageName = packageName,
                appName = preferences.getString(key(packageName, "name"), null) ?: return null,
                monogram = preferences.getString(key(packageName, "monogram"), null) ?: "?",
                tint = preferences.getLong(key(packageName, "tint"), 0xFFE3F0FF),
                target = RouteTarget(
                    kind = kind,
                    label = preferences.getString(key(packageName, "label"), null) ?: kind.name,
                    subscriptionId = subscriptionId,
                    nodeId = nodeId,
                    groupId = groupId,
                ),
            )
        }

        // Use one fresh snapshot under the existing profile lock. The editor still merges on commit.
        internal fun writeAll(acquire: () -> SharedPreferences, routes: List<AppRoute>) {
            val packages = routes.mapTo(linkedSetOf()) { it.packageName }
            val preferences = acquire()
            val removedPackages = preferences.getStringSet(KEY_PACKAGES, emptySet()).orEmpty() - packages
            preferences.edit(commit = true) {
                putStringSet(KEY_PACKAGES, packages)
                removedPackages.forEach { packageName ->
                    ROUTE_FIELDS.forEach { field ->
                        remove(key(packageName, field))
                    }
                }
                routes.forEach { route ->
                    putString(key(route.packageName, "name"), route.appName)
                    putString(key(route.packageName, "monogram"), route.monogram)
                    putLong(key(route.packageName, "tint"), route.tint)
                    putString(key(route.packageName, "kind"), route.target.kind.name)
                    putString(key(route.packageName, "label"), route.target.label)
                    putString(key(route.packageName, "subscription"), route.target.subscriptionId)
                    putString(key(route.packageName, "node"), route.target.nodeId)
                    putString(key(route.packageName, "group"), route.target.groupId)
                }
            }
        }

        private fun key(packageName: String, field: String) = "route.$packageName.$field"

        private const val PREFERENCES_NAME = "app_routes_v1"
        const val KEY_PACKAGES = "route.packages"
        val ROUTE_FIELDS = listOf(
            "name",
            "monogram",
            "tint",
            "kind",
            "label",
            "subscription",
            "node",
            "group",
        )
        val PACKAGE_NAME = Regex("""[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+""")
    }
}
