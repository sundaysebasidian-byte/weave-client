package io.weave.client.subscription

import org.junit.Assert.*
import org.junit.Test

/** Synthetic CMFA DocumentsProvider metadata shapes from pinned official Picker source. */
class ClientSourceMigrationTest {
    private val a = "/00000000-0000-0000-0000-000000000001"
    private val b = "/00000000-0000-0000-0000-000000000002"
    private val dir = "vnd.android.document/directory"
    private val payload = "proxies: [{name: fixture, type: socks5, server: example.test, port: 1080}]"
    private val pipeline = SubscriptionPreparation(ClashProviderResolver(fetch = { error("No fixture fetch") }), SubscriptionPayloadParser())
    private fun source(id: String, available: Boolean = true) = ClientSourceRecord(ClientSourceEntry(id, id, available), "local://fixture") { payload }

    @Test fun catalogueMirrorsOfficialUUIDPathsAndDoesNotOpenUnselectedContents() {
        val reads = mutableListOf<String>()
        val rows = CmfaProfileCatalogue.records("/", CmfaDocument("/", "Configurations", dir), { id ->
            when (id) {
                "/" -> listOf(CmfaDocument(a, "First", dir), CmfaDocument(b, "Second", dir))
                else -> listOf(CmfaDocument("$id/config.yaml", "config.yaml", "text/yaml"))
            }
        }, { id -> reads += id; payload })
        assertTrue(reads.isEmpty())
        val session = ClientSourceSession()
        val list = session.prepare("CMFA", rows)
        val selected = session.select(list.token, setOf(list.entries[1].id))
        assertEquals("Second", selected.single().entry.name)
        assertEquals(payload, selected.single().read())
        assertEquals(listOf("$b/config.yaml"), reads)
        assertFalse(list.toString().contains(a))
        assertFalse(list.toString().contains("config.yaml"))
    }

    @Test fun missingConfigIsDisabledAndSingleGrantedFolderIsSupported() {
        val rows = CmfaProfileCatalogue.records(a, CmfaDocument(a, "Single", dir), { emptyList() }, { error("No read") })
        assertEquals("Single", rows.single().entry.name)
        assertFalse(rows.single().entry.available)
        val session = ClientSourceSession(); val list = session.prepare("CMFA", rows)
        assertThrows(IllegalArgumentException::class.java) { session.select(list.token, setOf(list.entries.single().id)) }
    }

    @Test fun maliciousDocumentPathsCannotEscapeGrantedProfile() {
        for (id in listOf("/../private", "$a/providers", "/not-a-uuid", a + "/..")) {
            assertThrows(IllegalArgumentException::class.java) {
                CmfaProfileCatalogue.records(id, CmfaDocument(id, "Bad", dir), { emptyList() }, { error("No read") })
            }
        }
    }

    @Test fun fileProvidersAreMaterializedFromGrantedRelativePathsBeforePreparation() {
        val raw = """proxy-providers:
  cache:
    type: file
    path: ./providers/cache.yaml
    override: {udp: true}
proxies: []
"""
        val paths = mutableListOf<String>()
        val resolved = CmfaProviderSnapshot.materialize(raw) { paths += it; payload }
        val prepared = pipeline.prepare(resolved, "local://user-selected-file")
        assertEquals(listOf("providers/cache.yaml"), paths)
        assertEquals(1, prepared.counts.providers)
        assertEquals(1, prepared.counts.imported)
        assertTrue(prepared.first.contains("udp: true"))
        assertFalse(prepared.first.contains("proxy-providers"))
    }

    @Test fun providerTraversalAndRecursiveCollectionsAreRejectedBeforeAnySave() {
        for (path in listOf("../other", "/providers/cache", "providers/../cache", "providers//cache", "providers/a\\b", "providers/a:b", "providers/")) {
            assertThrows(IllegalArgumentException::class.java) { CmfaProviderSnapshot.safeProviderPath(path) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            CmfaProviderSnapshot.materialize("proxy-providers: {cache: {type: file, path: providers/cache.yaml}}") {
                "proxy-providers: {nested: {type: file, path: providers/again.yaml}}"
            }
        }
    }

    @Test fun sourceCatalogueTokensReplaceExpireAndRejectUnknownOrTooManySelections() {
        var now = 0L
        val session = ClientSourceSession { now }
        val old = session.prepare("CMFA", listOf(source("one")))
        val current = session.prepare("CMFA", (1..21).map { source("$it") })
        assertThrows(IllegalArgumentException::class.java) { session.select(old.token, setOf("one")) }
        for (ids in listOf(emptySet(), setOf("unknown"), (1..21).map { "$it" }.toSet()))
            assertThrows(IllegalArgumentException::class.java) { session.select(current.token, ids) }
        now = 300_000_000_000L
        assertThrows(IllegalArgumentException::class.java) { session.select(current.token, setOf("1")) }
    }

    @Test fun selectedBatchMetadataExcludesSecretsAndConfirmationCannotReplay() {
        val session = MigrationBatchSession()
        val first = PreparedSourceMigration("First", "https://example.test/?token=synthetic-secret", pipeline.prepare(payload, "local://fixture"))
        val second = first.copy(name = "Second", source = "local://user-selected-file")
        val preview = session.prepare(listOf(first, second))
        assertEquals(listOf("First", "Second"), preview.entries.map { it.name })
        assertTrue(preview.entries.first().remote); assertFalse(preview.entries.last().remote)
        assertFalse(preview.toString().contains("synthetic-secret"))
        assertEquals(listOf(first, second), session.consume(preview.token))
        assertThrows(IllegalArgumentException::class.java) { session.consume(preview.token) }
    }

    @Test fun pendingBatchClearsOnCancelReplacementAndExpiry() {
        var now = 0L; val session = MigrationBatchSession { now }
        val record = PreparedSourceMigration("First", "local://fixture", pipeline.prepare(payload, "local://fixture"))
        val old = session.prepare(listOf(record)); val new = session.prepare(listOf(record))
        assertThrows(IllegalArgumentException::class.java) { session.consume(old.token) }
        session.clear(); assertThrows(IllegalArgumentException::class.java) { session.consume(new.token) }
        val expired = session.prepare(listOf(record)); now = 300_000_000_000L
        assertThrows(IllegalArgumentException::class.java) { session.consume(expired.token) }
    }

    @Test fun capabilitiesRecognizeOnlyVerifiedOfficialPackages() {
        assertEquals("com.github.metacubex.clash.meta.files", ClientSourceCapabilities.cmfaAuthority("com.github.metacubex.clash.meta"))
        assertNull(ClientSourceCapabilities.cmfaAuthority("com.v2ray.ang"))
        assertFalse(ClientSourceCapabilities.isCmfaAuthority("malicious.files"))
        assertTrue(ClientSourceCapabilities.isKaring("com.nebula.karing"))
        assertFalse(ClientSourceCapabilities.isKaring("x2ray"))
    }
}
