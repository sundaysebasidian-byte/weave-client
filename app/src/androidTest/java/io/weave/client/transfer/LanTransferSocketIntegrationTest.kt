package io.weave.client.transfer

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class LanTransferSocketIntegrationTest {
    @Test fun accidentalRequestsDoNotConsumeAValidShareAndTheActualTransferIsSingleUse() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("weaveIsolatedEmulator") == "1")
        check(Build.HARDWARE == "ranchu" && Build.MODEL.startsWith("sdk_gphone"))
        val server = OneTimeLanTransferServer()
        val plaintext = ByteArray(1024 * 1024) { (it % 253).toByte() }
        val link = server.start(plaintext)
        try {
            Socket().use { unrelated ->
                unrelated.connect(InetSocketAddress(link.host, link.port), 3_000)
                unrelated.soTimeout = 3_000
                unrelated.getOutputStream().write("GET / HTTP/1.1\r\nConnection: close\r\n\r\n".toByteArray())
                assertTrue(unrelated.getInputStream().bufferedReader().readLine().startsWith("HTTP/1.1 404 "))
            }
            val packet = LanTransferClient.fetch(link)
            assertArrayEquals(plaintext, LanTransferCodec.open(packet, link.key))
            assertTrue(runCatching { LanTransferClient.fetch(link) }.isFailure)
        } finally {
            server.stop()
        }
    }
}
