package io.weave.client.transfer

import org.junit.Assert.*
import org.junit.Test

class SubscriptionShareSelectionTest {
    @Test fun `empty selection never means share all`() {
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionShareSelection.validate(setOf("one", "private"), emptySet())
        }
    }

    @Test fun `stale selection is rejected instead of silently changing scope`() {
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionShareSelection.validate(setOf("one"), setOf("one", "removed"))
        }
    }

    @Test fun `encrypted share roundtrip contains only checked subscriptions`() {
        val fixtures = listOf(
            TransferSubscription("Public fixture", "", "socks5://127.0.0.1:1080", "one"),
            TransferSubscription("Private fixture", "", "socks5://127.0.0.1:1081", "private"),
        )
        val allowed = SubscriptionShareSelection.validate(fixtures.map { it.id }.toSet(), setOf("one"))
        val key = LanTransferCodec.randomKey()
        val packet = LanTransferCodec.seal(LanTransferCodec.encode(fixtures.filter { it.id in allowed }), key)
        val decoded = LanTransferCodec.decode(LanTransferCodec.open(packet, key))
        // Device-local IDs intentionally do not cross the LAN wire format.
        assertEquals(1, decoded.size)
        assertEquals("socks5://127.0.0.1:1080", decoded.single().payload)
        assertEquals(listOf("Public fixture"), decoded.map { it.name })
    }
}
