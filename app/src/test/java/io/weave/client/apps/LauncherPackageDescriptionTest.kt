package io.weave.client.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherPackageDescriptionTest {
    private data class Launcher(val name: String?, val label: String)

    @Test fun `only the first activity per package is described`() {
        val rows = listOf(
            Launcher("weave", "Self"),
            Launcher("one", "Original"),
            Launcher(null, "Missing"),
            Launcher("two", "Second"),
            Launcher("one", "Duplicate"),
            Launcher("two", "Duplicate again"),
            Launcher("One", "Different exact key"),
        )
        val described = mutableListOf<String>()
        val apps = describeDistinctLauncherPackages(rows, "weave", Launcher::name) { row, name ->
            described += row.label
            InstalledApp(name, row.label, "", 0)
        }
        assertEquals(listOf("one", "two", "One"), apps.map { it.packageName })
        assertEquals(listOf("Original", "Second", "Different exact key"), described)
    }

    @Test fun `single final sort retains baseline tie order with known apps`() {
        val rows = listOf(
            Launcher("one", "beta"), Launcher("two", "same"),
            Launcher("three", "alpha"), Launcher("four", "SAME"),
            Launcher("one", "other"),
        )
        val known = listOf(InstalledApp("hidden", "Same", "", 0))
        val byLabel = compareBy<InstalledApp, String>(String.CASE_INSENSITIVE_ORDER) { it.label }
        val baseline = (rows.map { InstalledApp(checkNotNull(it.name), it.label, "", 0) }
            .distinctBy { it.packageName }.sortedWith(byLabel) + known)
            .distinctBy { it.packageName }.sortedWith(byLabel)
        val selected = describeDistinctLauncherPackages(rows, "weave", Launcher::name) { row, name ->
            InstalledApp(name, row.label, "", 0)
        }
        val actual = (selected + known).distinctBy { it.packageName }.sortedWith(byLabel)
        assertEquals(listOf("three", "one", "two", "four", "hidden"), actual.map { it.packageName })
        assertEquals(baseline, actual)
    }
}
