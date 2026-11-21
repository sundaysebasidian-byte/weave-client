package io.weave.client.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class MissingKnownAppsTest {
    @Test fun onlyQueriesKnownPackagesWithoutLauncherRows() {
        fun app(pkg: String) = InstalledApp(pkg, pkg, "A", 0L)
        val launchable = listOf(app("ordinary"), app("visible"))
        val calls = mutableListOf<String>()
        val extras = missingKnownApps(launchable, linkedSetOf("visible", "hidden", "absent")) {
            calls += it
            if (it == "absent") null else app(it)
        }
        assertEquals(listOf("hidden", "absent"), calls)
        assertEquals(listOf(app("hidden")), extras)
    }
}
