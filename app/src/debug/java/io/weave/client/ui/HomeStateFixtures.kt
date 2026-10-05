package io.weave.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.weave.client.domain.ConnectionState
import io.weave.client.domain.DashboardState
import io.weave.client.domain.NetworkPathStatus
import io.weave.client.domain.ProxyNode
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.RoutingMode
import io.weave.client.domain.WeaveLanguage
import io.weave.client.domain.WeavePalette
import io.weave.client.ui.theme.WeaveTheme

/**
 * Local-only UI states for the Home screen, shared by Android Studio previews and instrumented
 * layout checks. Debug source set: nothing here ships in release builds, starts a VPN, grants a
 * permission or touches the network. Every value is a fixture, not a measurement.
 *
 * Names ending in `-sample-traffic` carry made-up throughput and latency to stress the layout
 * with a chart present; do not use them for product imagery. The other connected cases
 * deliberately show no traffic or latency numbers.
 */
internal object HomeFixtures {
    class Case(
        val name: String,
        val state: DashboardState,
        val trafficHistory: List<Long> = emptyList(),
    )

    private val autoExit = RouteTarget(RouteKind.AUTO, "自动选择")
    private val invalidExit = RouteTarget(RouteKind.BLOCK, INVALID_EXIT_LABEL)
    private val node = ProxyNode(
        id = "fixture-node",
        name = "Fixture Node 01",
        region = "XX",
        subscriptionId = "fixture-subscription",
        protocol = "Hysteria2",
        latencyMs = null,
    )

    private fun connected(path: NetworkPathStatus) = DashboardState(
        connectionState = ConnectionState.CONNECTED,
        coreAvailable = true,
        defaultRouteTarget = autoExit,
        activeNode = node,
        networkPathStatus = path,
    )

    val cases: List<Case> = listOf(
        Case("disconnected-ready", DashboardState(coreAvailable = true, defaultRouteTarget = autoExit)),
        Case("disconnected-no-exit", DashboardState(coreAvailable = true)),
        Case("disconnected-invalid-exit", DashboardState(coreAvailable = true, defaultRouteTarget = invalidExit)),
        Case(
            "disconnected-direct-mode",
            DashboardState(coreAvailable = true, routingMode = RoutingMode.DIRECT, defaultRouteTarget = autoExit),
        ),
        Case("disconnected-core-missing", DashboardState(coreAvailable = false)),
        Case(
            "connecting",
            DashboardState(
                connectionState = ConnectionState.CONNECTING,
                coreAvailable = true,
                defaultRouteTarget = autoExit,
                networkPathStatus = NetworkPathStatus.STARTING,
            ),
        ),
        Case("connected-unverified", connected(NetworkPathStatus.TUN_READY)),
        Case("connected-verified", connected(NetworkPathStatus.VERIFIED)),
        Case("connected-recovering", connected(NetworkPathStatus.RECOVERING)),
        Case("connected-waiting-network", connected(NetworkPathStatus.WAITING_NETWORK)),
        Case(
            "connected-sample-traffic",
            connected(NetworkPathStatus.VERIFIED).copy(
                activeNode = node.copy(latencyMs = 120),
                downloadBytesPerSecond = 6_000,
                uploadBytesPerSecond = 2_000,
            ),
            trafficHistory = listOf(0L, 800L, 1_900L, 1_200L, 3_600L, 5_900L, 5_100L, 7_400L, 6_000L),
        ),
        Case(
            "error-with-message",
            DashboardState(
                connectionState = ConnectionState.ERROR,
                coreAvailable = true,
                defaultRouteTarget = autoExit,
                statusMessage = "Fixture: configuration rejected",
            ),
        ),
        Case(
            "error-no-message",
            DashboardState(connectionState = ConnectionState.ERROR, coreAvailable = true, defaultRouteTarget = autoExit),
        ),
        Case(
            "error-core-missing",
            DashboardState(connectionState = ConnectionState.ERROR, coreAvailable = false, defaultRouteTarget = autoExit),
        ),
    )

    fun byName(name: String): Case = cases.first { it.name == name }
}

/**
 * The app's own backdrop (theme background plus the atmosphere layer) under whatever screen is
 * being previewed or captured, so a capture looks like the app and not like a bare composable.
 * The navigation dock is intentionally absent: it is private to WeaveApp and outside every crop
 * the CG uses.
 */
@Composable
internal fun FixtureShell(
    language: WeaveLanguage = WeaveLanguage.SIMPLIFIED_CHINESE,
    fontScale: Float = 1f,
    palette: WeavePalette = WeavePalette.MINIMAL_LIGHT,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalWeaveLanguage provides language,
        LocalDensity provides Density(LocalDensity.current.density, fontScale),
    ) {
        WeaveTheme(palette = palette, darkTheme = palette.forceDark) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                MonetAtmosphere(palette = palette, modifier = Modifier.fillMaxSize())
                content()
            }
        }
    }
}

@Composable
internal fun HomeFixtureBody(case: HomeFixtures.Case) {
    HomeScreen(
        onScrolling = {},
        state = case.state,
        trafficHistory = case.trafficHistory,
        onConnect = {},
        onModeSelected = {},
        onDefaultRouteClick = {},
        onMoreClick = {},
        onIpQuality = {},
        contentPadding = PaddingValues(0.dp),
    )
}

@Composable
internal fun HomeFixtureContent(
    case: HomeFixtures.Case,
    language: WeaveLanguage = WeaveLanguage.SIMPLIFIED_CHINESE,
    fontScale: Float = 1f,
    palette: WeavePalette = WeavePalette.MINIMAL_LIGHT,
) {
    FixtureShell(language = language, fontScale = fontScale, palette = palette) { HomeFixtureBody(case) }
}

@Preview(name = "Home · disconnected", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun PreviewHomeDisconnected() = HomeFixtureContent(HomeFixtures.byName("disconnected-ready"))

@Preview(name = "Home · invalid exit", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun PreviewHomeInvalidExit() = HomeFixtureContent(HomeFixtures.byName("disconnected-invalid-exit"))

@Preview(name = "Home · connecting", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun PreviewHomeConnecting() = HomeFixtureContent(HomeFixtures.byName("connecting"))

@Preview(name = "Home · connected, exit not verified", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun PreviewHomeConnectedUnverified() = HomeFixtureContent(HomeFixtures.byName("connected-unverified"))

@Preview(name = "Home · connected, exit verified", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun PreviewHomeConnectedVerified() = HomeFixtureContent(HomeFixtures.byName("connected-verified"))

@Preview(name = "Home · error with reason", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun PreviewHomeError() = HomeFixtureContent(HomeFixtures.byName("error-with-message"))

@Preview(name = "Home · core missing", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun PreviewHomeCoreMissing() = HomeFixtureContent(HomeFixtures.byName("disconnected-core-missing"))

@Preview(name = "Home · narrow, 2x type, connected, English", widthDp = 320, heightDp = 1100, showBackground = true)
@Composable
private fun PreviewHomeNarrowDoubleTypeConnected() = HomeFixtureContent(
    HomeFixtures.byName("connected-unverified"),
    language = WeaveLanguage.ENGLISH,
    fontScale = 2f,
)

@Preview(name = "Home · narrow, large type, German", widthDp = 320, heightDp = 900, showBackground = true)
@Composable
private fun PreviewHomeNarrowLargeGerman() = HomeFixtureContent(
    HomeFixtures.byName("error-with-message"),
    language = WeaveLanguage.GERMAN,
    fontScale = 1.5f,
)
