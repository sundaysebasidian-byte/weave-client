package io.weave.client.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.domain.*
import io.weave.client.ui.theme.WeaveTheme
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Motion QA only: plays the production home screen through a whole connection on the real clock,
 * for a screen recording. Skipped unless the runner passes `-e qaReel true`; never starts a VPN.
 */
class HomeMotionReelTest {
    @Test fun playConnectionReel() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("qaReel") == "true")
        val palette = WeavePalette.valueOf(args.getString("qaPalette", "MINIMAL_LIGHT"))
        val connection = mutableStateOf(ConnectionState.DISCONNECTED)
        val traffic = mutableLongStateOf(0L)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    CompositionLocalProvider(LocalWeaveLanguage provides WeaveLanguage.SIMPLIFIED_CHINESE) {
                        WeaveTheme(palette = palette) {
                            Surface(Modifier.fillMaxSize()) {
                                val state = connection.value
                                HomeScreen(
                                    {},
                                    DashboardState(
                                        connectionState = state,
                                        coreAvailable = true,
                                        hasUsableSubscription = true,
                                        defaultRouteTarget = RouteTarget(RouteKind.AUTO, "示例出口"),
                                        networkPathStatus = if (state == ConnectionState.CONNECTED)
                                            NetworkPathStatus.TUN_READY else NetworkPathStatus.INACTIVE,
                                        downloadBytesPerSecond = if (state == ConnectionState.CONNECTED) traffic.longValue else 0L,
                                        uploadBytesPerSecond = if (state == ConnectionState.CONNECTED) traffic.longValue / 9 else 0L,
                                        statusMessage = if (state == ConnectionState.ERROR) "示例错误：配置不可用" else null,
                                    ),
                                    onConnect = {}, onModeSelected = {}, onDefaultRouteClick = {},
                                    onMoreClick = {}, onIpQuality = {}, contentPadding = PaddingValues(0.dp),
                                )
                            }
                        }
                    }
                }
            }
            fun then(waitMs: Long, change: () -> Unit) {
                Thread.sleep(waitMs)
                instrumentation.runOnMainSync(change)
            }
            then(3000) { connection.value = ConnectionState.CONNECTING }
            then(2300) { connection.value = ConnectionState.CONNECTED }
            then(2800) { traffic.longValue = 40_000 }
            then(1500) { traffic.longValue = 600_000 }
            then(1500) { traffic.longValue = 4_000_000 }
            then(1500) { traffic.longValue = 22_000_000 }
            then(3000) { traffic.longValue = 0 }
            then(1200) { connection.value = ConnectionState.DISCONNECTED }
            then(2600) { connection.value = ConnectionState.CONNECTING }
            then(1300) { connection.value = ConnectionState.ERROR }
            Thread.sleep(3000)
        }
    }
}
