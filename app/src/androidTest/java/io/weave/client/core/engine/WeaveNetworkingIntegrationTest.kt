package io.weave.client.core.engine

import io.weave.client.core.diagnostics.LanShareTopology
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.domain.DnsProfile
import io.weave.client.domain.NetworkPreferences
import io.weave.client.domain.RoutingMode
import io.weave.client.subscription.ClashYamlCodec
import kotlinx.coroutines.runBlocking
import io.weave.client.routing.CustomProxyGroup
import io.weave.client.routing.NodeRef
import io.weave.client.routing.readCustomProxyGroup
import io.weave.client.routing.toJson
import io.weave.client.transfer.QrCodeGenerator
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WeaveNetworkingIntegrationTest {
    @Test fun nativeValidationAcceptsMixedEncryptedResolversAndRealIpMode() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolvers = listOf("https://dns.google/dns-query", "tls://dns.quad9.net", "quic://dns.adguard-dns.com")
        val yaml = MihomoConfigAssembler(context).assemble(
            emptyList(),
            RoutingMode.DIRECT,
            networkPreferences = NetworkPreferences(
                dnsProfile = DnsProfile.CUSTOM,
                customDnsEndpoint = resolvers.joinToString("\n"),
                fakeIp = false,
            ),
        ).yaml
        val config = ClashYamlCodec.read(yaml)["dns"] as Map<*, *>
        assertEquals(resolvers, config["nameserver"])
        assertEquals(resolvers, config["proxy-server-nameserver"])
        assertEquals("redir-host", config["enhanced-mode"])
        MihomoEngineAdapter(context).validate(yaml).getOrThrow()
    }
    @Test fun shareQrArtworkDecodesAtDefaultAndSmallRequestedSizes() {
        val value = "socks5://weave-test:fixture-password@192.168.1.2:7890#Weave%20Share"
        for (size in listOf(512, 24)) {
            val image = QrCodeGenerator.create(value, size)
            try {
                val pixels = IntArray(image.width * image.height)
                image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
                val source = RGBLuminanceSource(image.width, image.height, pixels)
                val decoded = try {
                    QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
                } catch (error: com.google.zxing.ReaderException) {
                    throw AssertionError("Generated QR is unreadable at requested size $size", error)
                }
                assertEquals(value, decoded)
            } finally {
                image.recycle()
            }
        }
    }
    @Test fun chainSerializationKeepsBackwardCompatibilityAndOrderedHops() {
        val group = CustomProxyGroup(
            name = "Fixture",
            entry = NodeRef("s1", "entry"),
            members = listOf(NodeRef("s2", "exit")),
            via = listOf(NodeRef("s3", "first"), NodeRef("s4", "second")),
            entryFallbacks = listOf(NodeRef("s4", "backup")),
        )
        val original = group.copy(via = emptyList(), entryFallbacks = emptyList())
        val legacy = original.toJson()
        assertFalse(legacy.has("via"))
        assertEquals(original, readCustomProxyGroup(legacy))
        assertEquals(group, readCustomProxyGroup(group.toJson()))
    }

    @Test fun shareTopologyCountsOnlyUniqueAuthenticatedPrivateSessionsOnSharePort() {
        fun row(id: String, source: String, user: String, port: Int = 7890, upload: Long = 17): JSONObject =
            JSONObject().put("id", id).put("upload", upload).put("download", 34)
                .put("chains", JSONArray(listOf("fixed-exit")))
                .put("metadata", JSONObject().put("sourceIP", source).put("inboundUser", user)
                    .put("inboundPort", port.toString()).put("host", "excluded.example"))
        val rows = listOf(
            row("one", "192.168.1.2", "allowed"), row("two", "192.168.1.2", "allowed"),
            row("two", "192.168.1.2", "allowed"), row("three", "192.168.1.3", "allowed", upload = Long.MAX_VALUE),
            row("four", "192.168.1.3", "allowed"), row("public", "8.8.8.8", "allowed"),
            row("loopback", "127.0.0.1", "allowed"), row("anon", "192.168.1.4", ""),
            row("other", "192.168.1.5", "allowed", port = 8900),
            row("label", "bad.example", "allowed"), row("trail/ing", "192.168.1.6", "allowed"),
        )
        val devices = LanShareTopology.read(JSONObject().put("connections", JSONArray(rows)))
        assertEquals(listOf("192.168.1.2", "192.168.1.3"), devices.map { it.address })
        assertEquals(listOf("one", "two"), devices.first().connectionIds)
        assertEquals(34L, devices.first().uploaded)
        assertEquals(68L, devices.first().downloaded)
        assertEquals(listOf("fixed-exit"), devices.first().exits)
        assertEquals(Long.MAX_VALUE, devices.last().uploaded)
    }

    @Test fun shareTopologySeparatesConcurrentGrantsFromOneAddress() {
        val address = "192.168.1.7"
        val first = "weavepeer-${"1".repeat(32)}-10000-1024"
        val second = "weavepeer-${"2".repeat(32)}-10000-1024"
        val records = listOf(first to 7891, second to 7891, "legacy" to 7890)
        val rows = JSONArray(records.mapIndexed { index, (user, port) ->
            JSONObject().put("id", "socket-$index").put("upload", index + 1).put(
                "metadata", JSONObject().put("sourceIP", address).put("inboundUser", user).put("inboundPort", port),
            )
        })
        val byGrant = LanShareTopology.read(JSONObject().put("connections", rows)).associateBy { it.key.grantUser }
        assertEquals(listOf("socket-0"), byGrant.getValue(first).connectionIds)
        assertEquals(listOf("socket-1"), byGrant.getValue(second).connectionIds)
        assertEquals(3L, byGrant.getValue(null).uploaded)
    }

}
