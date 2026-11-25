package io.weave.client.transfer

import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class SubscriptionFileCodecTest {
    private val password = "offline example passphrase".toCharArray()
    private val id = "00000000-0000-4000-8000-000000000001"
    private val otherId = "00000000-0000-4000-8000-000000000002"
    private val fixture = TransferSubscription("本地 🚇", "https://example.invalid/sub?token=fixture-only",
        "proxies: [{name: Node, type: socks5, server: example.invalid, port: 1080}]", id)

    @Test fun sameNameLocalImportsAreDistinctById() {
        val first = fixture.copy(source = "local://user-selected-file")
        val second = first.copy(id = otherId, payload = first.payload.replace("1080", "1081"))
        assertEquals(listOf(first, second),
            SubscriptionFileCodec.decrypt(SubscriptionFileCodec.encrypt(listOf(first, second), password), password))
    }

    @Test fun filesRoundTripOldNamesThatWereTruncatedOnASpace() {
        val old = fixture.copy(name = "x".repeat(79) + " ")
        assertEquals(listOf(old), SubscriptionFileCodec.decrypt(SubscriptionFileCodec.encrypt(listOf(old), password), password))
    }

    @Test fun selectedSourceNodesAndLocalIdentitySurviveOfflineRoundTrip() {
        val other = fixture.copy(id = otherId, name = "Second", source = "inline://pasted-payload")
        val both = listOf(fixture, other)
        val encrypted = SubscriptionFileCodec.encrypt(both, password)
        val bytesAsText = encrypted.toString(Charsets.ISO_8859_1)
        for (secret in listOf("fixture-only", "example.invalid", "Second", "proxies:", id)) assertFalse(bytesAsText.contains(secret))
        assertEquals(both, SubscriptionFileCodec.decrypt(encrypted, password))
        val single = SubscriptionFileCodec.encrypt(listOf(other), password)
        assertEquals(listOf(other), SubscriptionFileCodec.decrypt(single, password))
        assertFalse(single.contentEquals(SubscriptionFileCodec.encrypt(listOf(other), password)))
    }

    @Test fun wrongPasswordAndCorruptCiphertextCannotProduceAPlan() {
        val bytes = SubscriptionFileCodec.encrypt(listOf(fixture), password)
        assertThrows(BackupException::class.java) { SubscriptionFileCodec.decrypt(bytes, "incorrect password".toCharArray()) }
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertThrows(BackupException::class.java) { SubscriptionFileCodec.decrypt(bytes, password) }
    }

    @Test fun fullProfileBackupsAndMixedArchivesAreNotAcceptedAsPartialTransfers() {
        for (version in 1..2) {
            val entries = mapOf("manifest.json" to "{\"format\":\"weave-backup\",\"version\":$version}")
            val bytes = BackupCodec.encrypt(entries, password, iterations = 100_000, formatVersion = version)
            assertThrows(IllegalArgumentException::class.java) { SubscriptionFileCodec.decrypt(bytes, password) }
            assertEquals(entries, BackupCodec.decrypt(bytes, password))
        }
        val entries = BackupCodec.decrypt(SubscriptionFileCodec.encrypt(listOf(fixture), password), password)
        assertEquals(setOf("sub-$id.json"), entries.keys)
        val mixed = BackupCodec.encrypt(entries + ("settings.json" to "{}"), password, iterations = 100_000)
        assertThrows(IllegalArgumentException::class.java) { SubscriptionFileCodec.decrypt(mixed, password) }
    }

    @Test fun duplicateIdentitiesAndAmbiguousMatchingAreRejectedBeforeExport() {
        listOf(emptyList(), listOf(fixture, fixture.copy(name = "Different")),
            listOf(fixture, fixture.copy(id = otherId)), listOf(fixture.copy(id = "not-an-id")),
            listOf(fixture.copy(name = "   ")), listOf(fixture.copy(name = "Broken\uD800"))).forEach { items ->
            assertThrows(IllegalArgumentException::class.java) { SubscriptionFileCodec.encrypt(items, password) }
        }
    }

    @Test fun evenAuthenticatedDataCannotSmuggleMultipleRecordsOrAmbiguousTargets() {
        fun archive(values: Map<String,String>) = BackupCodec.encrypt(values, password, iterations = 100_000)
        fun packet(items: List<TransferSubscription>) = "\"" + Base64.getEncoder().encodeToString(LanTransferCodec.encode(items)) + "\""
        val key = "sub-$id.json"
        listOf(mapOf(key to packet(listOf(fixture, fixture))),
            mapOf(key to packet(listOf(fixture)), "sub-$otherId.json" to packet(listOf(fixture))),
            mapOf(key to "not a packet"), mapOf(key to "\"!!!!\"")).forEach { entries ->
            assertThrows(IllegalArgumentException::class.java) { SubscriptionFileCodec.decrypt(archive(entries), password) }
        }
    }

    @Test fun lanConfirmationRejectsWrongOrAbsentCodeBeforeFetchingTheOneTimePacket() {
        val link = LanTransferLink("127.0.0.1", 8080, "0".repeat(32), ByteArray(32))
        val right = link.confirmationCode()
        val wrong = if (right == "000000") "000001" else "000000"
        listOf("", "  ", "123", "1234567", "abcdef", wrong).forEach { bad ->
            assertThrows(IllegalStateException::class.java) { link.requireConfirmation(bad) }
        }
        link.requireConfirmation(" $right ")
    }

    @Test fun refusesInvalidPassphraseAndOversizeItemsWithoutTruncating() {
        assertThrows(BackupException::class.java) { SubscriptionFileCodec.encrypt(listOf(fixture), "123456".toCharArray()) }
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionFileCodec.encrypt(listOf(fixture.copy(payload = "x".repeat(5 * 1024 * 1024 + 1))), password)
        }
    }
}
