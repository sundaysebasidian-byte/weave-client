package io.weave.client.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
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
        val actual = sortDistinctLauncherAndKnownApps(selected, known)
        assertEquals(listOf("three", "one", "two", "four", "hidden"), actual.map { it.packageName })
        assertEquals(baseline, actual)
    }

    @Test fun mergeMatchesPreviousOrderAndIdentityAfterBothPackageFilters() {
        val random = kotlin.random.Random(115)
        val labels = listOf("", "x", "X", "É", "é", "i", "İ", "I", "APP", "app", "😊")
        val order = compareBy<InstalledApp, String>(String.CASE_INSENSITIVE_ORDER) { it.label }
        repeat(2048) { sample ->
            val raw = List(sample % 43) {
                Launcher(if (it % 29 == 0) null else "pkg-${random.nextInt(60)}", labels.random(random))
            }
            val selected = describeDistinctLauncherPackages(raw, "pkg-5", Launcher::name) { row, pkg ->
                InstalledApp(pkg, row.label, "L", 0)
            }
            val requested = List(sample % 21) { "pkg-${random.nextInt(60)}" }.toSet()
            val known = requested.mapNotNull { pkg ->
                if (pkg == "pkg-7") null else InstalledApp(pkg, labels.random(random), "K", 1)
            }

            val before = (selected + known).distinctBy { it.packageName }.sortedWith(order)
            val after = sortDistinctLauncherAndKnownApps(selected, known)
            assertEquals(before, after)
            before.indices.forEach { assertSame(before[it], after[it]) }
        }
    }

    @Test fun mergeRetainsJvmListShapesAndKeepsInputRowsUntouched() {
        val a = InstalledApp("a", "a", "", 0)
        val b = InstalledApp("b", "B", "", 0)
        val order = compareBy<InstalledApp, String>(String.CASE_INSENSITIVE_ORDER) { it.label }
        for ((launcher, known) in listOf(
            emptyList<InstalledApp>() to emptyList(),
            listOf(a) to emptyList(),
            emptyList<InstalledApp>() to listOf(b),
            listOf(b) to listOf(a),
        )) {
            val before = (launcher + known).distinctBy { it.packageName }.sortedWith(order)
            val after = sortDistinctLauncherAndKnownApps(launcher, known)
            assertEquals(before.javaClass, after.javaClass)
        }

        val launcher = mutableListOf(b)
        val known = mutableListOf(a)
        val result = sortDistinctLauncherAndKnownApps(launcher, known) as MutableList<InstalledApp>
        assertSame(a, result.set(0, b))
        result[1] = a
        result.sortWith(order)
        assertEquals(listOf(a, b), result)
        assertEquals(listOf(b), launcher)
        assertEquals(listOf(a), known)
        try {
            result.add(a)
            fail("a sorted JVM fixed-size array view does not support add")
        } catch (_: UnsupportedOperationException) {
        }
    }
}
