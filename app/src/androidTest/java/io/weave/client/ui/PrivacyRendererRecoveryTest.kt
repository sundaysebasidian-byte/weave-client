package io.weave.client.ui

import android.webkit.RenderProcessGoneDetail
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyRendererRecoveryTest {
    @Test fun rendererFailureReportsOnceAndReleasesTheOwnedView() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            var failures = 0
            val view = PrivacyProbeHostWebView(instrumentation.targetContext)
            val client = PrivacyProbeClient(onResult = { fail("A failed renderer must not report success") }, onError = { failures++ })
            view.probeClient = client
            view.webViewClient = client
            val details = object : RenderProcessGoneDetail() {
                override fun didCrash() = true
                override fun rendererPriorityAtExit() = 0
            }
            assertTrue(client.onRenderProcessGone(view, details))
            assertTrue(view.released.get())
            assertEquals(1, failures)
            // Compose disposal and a late callback are harmless after the first release.
            releasePrivacyProbeWebView(view)
            assertTrue(client.onRenderProcessGone(view, details))
            assertEquals(1, failures)
        }
    }
}
