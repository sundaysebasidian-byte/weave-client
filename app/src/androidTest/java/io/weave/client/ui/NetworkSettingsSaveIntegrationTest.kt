package io.weave.client.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.MainActivity
import io.weave.client.data.ProfileStorage
import io.weave.client.data.RuntimeSettingsStore
import io.weave.client.domain.DnsTransport
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class NetworkSettingsSaveIntegrationTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun settingsStayResponsiveDuringDiskContentionAndSaveInTapOrder() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("isolatedRoutingTest") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = RuntimeSettingsStore(context)
        val before = settings.networkPreferences().dnsTransport
        val first = DnsTransport.entries.first { it != before }
        val last = before
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.waitUntil(30_000) {
                    runCatching { compose.onNodeWithTag("navigation-tab-HOME").assertIsDisplayed() }.isSuccess
                }
                lateinit var model: AppViewModel
                scenario.onActivity { model = ViewModelProvider(it)[AppViewModel::class.java] }
                runBlocking { withContext(Dispatchers.Main) { model.awaitReady() } }

                val diskLocked = CountDownLatch(1)
                val releaseDisk = CountDownLatch(1)
                val tapsReturned = CountDownLatch(1)
                val holder = thread(name = "settings-lock-fixture") {
                    ProfileStorage.access(context) {
                        diskLocked.countDown()
                        releaseDisk.await(20, TimeUnit.SECONDS)
                    }
                }
                try {
                    assertTrue("The fixture must hold the profile lock", diskLocked.await(5, TimeUnit.SECONDS))
                    Handler(Looper.getMainLooper()).post {
                        model.setDnsTransport(first)
                        model.setDnsTransport(last)
                        model.setDnsTransport(first)
                        tapsReturned.countDown()
                    }
                    assertTrue("Settings taps must return while the profile is locked", tapsReturned.await(5, TimeUnit.SECONDS))
                } finally {
                    releaseDisk.countDown()
                    holder.join(5_000)
                }

                runBlocking { withContext(Dispatchers.Main) { model.awaitRoutingSave() } }
                assertEquals(first, model.networkPreferences.value.dnsTransport)
                assertEquals(first, settings.networkPreferences().dnsTransport)
            }
        } finally {
            settings.setDnsTransport(before)
        }
    }
}
