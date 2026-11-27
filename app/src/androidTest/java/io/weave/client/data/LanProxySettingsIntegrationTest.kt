package io.weave.client.data

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import androidx.test.platform.app.InstrumentationRegistry
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanProxySettingsIntegrationTest {
    @Test fun concurrentReadersSeeTheCredentialsCommittedWithSharingAndTheySurviveDisable() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(app.cacheDir.toPath(), "lan-settings-").toFile()
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir() = root
            override fun getApplicationInfo() = ApplicationInfo(app.applicationInfo).apply { dataDir = root.absolutePath }
        }
        val read = RuntimeSettingsStore(context)
        val write = RuntimeSettingsStore(context)
        val executor = Executors.newFixedThreadPool(2)
        val begin = CountDownLatch(1)
        try {
            val reader = executor.submit<Pair<String, String>> {
                check(begin.await(5, TimeUnit.SECONDS))
                read.lanProxyCredentials()
            }
            val enabler = executor.submit<Pair<String, String>> {
                check(begin.await(5, TimeUnit.SECONDS))
                write.enableLanSharing()
            }
            begin.countDown()
            val credentials = enabler.get(10, TimeUnit.SECONDS)
            assertEquals(credentials, reader.get(10, TimeUnit.SECONDS))
            assertTrue(read.networkPreferences().lanSharing)
            write.setLanSharing(false)
            assertFalse(read.networkPreferences().lanSharing)
            assertEquals(credentials, write.enableLanSharing())
            assertTrue(read.networkPreferences().lanSharing)
        } finally {
            executor.shutdownNow()
            root.deleteRecursively()
        }
    }
}
