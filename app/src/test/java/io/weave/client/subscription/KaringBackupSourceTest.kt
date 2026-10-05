package io.weave.client.subscription

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class KaringBackupSourceTest {
    private val pipeline = SubscriptionPreparation(ClashProviderResolver(fetch = { error("No remote fixture fetch") }), SubscriptionPayloadParser())
    private fun official(): ByteArray = javaClass.getResourceAsStream("/karing-official-1.2.25.2802-synthetic.backup.zip")!!.readBytes()
    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip -> entries.forEach { (name, value) -> zip.putNextEntry(ZipEntry(name)); zip.write(value); zip.closeEntry() } }
    }.toByteArray()
    private fun group(id: String = "one", options: String = "") = """{"groupid":"$id","remark":"Synthetic","servers":[{"type":"socks","tag":"Fixture","server":"127.0.0.1","server_port":1080$options}]}"""
    private fun backup(items: String): ByteArray = zip("karing_subscribe.json" to "{\"items\":[$items]}".toByteArray())

    @Test fun verifiedOfficialExportShapeYieldsRealGroupsAndOneExactNode() {
        val records = KaringBackupSource().catalogue(official().inputStream())
        assertEquals(2, records.size)
        assertEquals("Custom", records.first().entry.name)
        assertFalse(records.first().entry.available)
        val imported = records.last()
        assertEquals("WeaveSynthetic109-A.yaml", imported.entry.name)
        assertTrue(imported.entry.available)
        assertEquals("local://user-selected-file", imported.source)
        val prepared = pipeline.prepare(imported.read(), imported.source)
        assertEquals(SubscriptionFormat.SING_BOX_JSON, prepared.inputFormat)
        assertEquals(1, prepared.counts.imported)
        assertEquals("Synthetic-A", prepared.second.nodes.single().name)
        assertFalse(imported.read().contains("groupid"))
        assertFalse(imported.read().contains("data/user"))
    }

    @Test fun multipleActualGroupsCanBeSelectedWithoutReadingOtherContentsOrFetchingUrls() {
        val records = KaringBackupSource().catalogue(backup(group("one") + "," + group("two")).inputStream())
        val session = ClientSourceSession(); val list = session.prepare("Karing", records)
        val selected = session.select(list.token, records.map { it.entry.id }.toSet())
        assertEquals(2, selected.size)
        val batch = MigrationBatchSession().prepare(selected.map { PreparedSourceMigration(it.entry.name, it.source, pipeline.prepare(it.read(), it.source)) })
        assertEquals(listOf(1, 1), batch.entries.map { it.counts.imported })
        assertTrue(batch.entries.none { it.remote })
    }

    @Test fun fileTypeOrFilenameAloneCannotProduceAnInventedCatalogue() {
        for (bytes in listOf("plain text".toByteArray(), zip("profiles.json" to "{}".toByteArray()), zip("karing_subscribe.json" to "{\"profiles\":[]}".toByteArray())))
            assertTrue(runCatching { KaringBackupSource().catalogue(bytes.inputStream()) }.isFailure)
    }

    @Test fun maliciousArchivePathsNeverExtractOrResolveSourceAppPrivatePaths() {
        for (name in listOf("../private", "/data/user/private", "a/../b", "a\\b", "a:b", "a//b"))
            assertTrue(runCatching { KaringBackupSource().catalogue(zip(name to byteArrayOf(1)).inputStream()) }.isFailure)
    }

    @Test fun tooManyEntriesAndOversizeDecompressionAreBounded() {
        val many = (1..129).map { "file$it" to byteArrayOf(1) }.toTypedArray()
        assertTrue(runCatching { KaringBackupSource().catalogue(zip(*many).inputStream()) }.isFailure)
        assertTrue(runCatching { KaringBackupSource().catalogue(zip("karing_subscribe.json" to ByteArray(6 * 1024 * 1024) { 32 }).inputStream()) }.isFailure)
        assertTrue(runCatching { KaringBackupSource().catalogue(zip("unrelated" to ByteArray(21 * 1024 * 1024)).inputStream()) }.isFailure)
    }

    @Test fun unknownNetworkOptionsDisableWholeGroupInsteadOfDroppingNodesOrOptions() {
        for (options in listOf(",\"password\":[\"bad\"]", ",\"tls\":{\"enabled\":\"true\"}", ",\"multiplex\":{\"enabled\":true}", ",\"tls\":{\"enabled\":true,\"utls\":{\"enabled\":true}}", ",\"transport\":{\"type\":\"httpupgrade\"}")) {
            val record = KaringBackupSource().catalogue(backup(group(options = options)).inputStream()).single()
            assertFalse(record.entry.available)
            assertTrue(record.entry.reason!!.contains("未支持"))
            assertTrue(runCatching { record.read() }.isFailure)
        }
    }

    @Test fun malformedGroupsAndDuplicateGroupIdsAreRejected() {
        for (items in listOf(group("same") + "," + group("same"), "{\"groupid\":\"one\",\"servers\":{}}", "{\"servers\":[]}", "null"))
            assertTrue(runCatching { KaringBackupSource().catalogue(backup(items).inputStream()) }.isFailure)
    }

    @Test fun malformedUtf8AndCorruptedZipCannotProduceChoices() {
        assertTrue(runCatching { KaringBackupSource().catalogue(zip("karing_subscribe.json" to byteArrayOf(0xc3.toByte(), 0x28)).inputStream()) }.isFailure)
        val bytes = official().copyOf(20)
        assertTrue(runCatching { KaringBackupSource().catalogue(bytes.inputStream()) }.isFailure)
    }

    @Test fun dataOnlyJsonEncoderPreservesEscapesAndRejectsNonFiniteValues() {
        val map = mapOf("text" to "quote\"\\line\n\t😀", "items" to listOf(1, true, null))
        assertEquals(map, ClashYamlCodec.read(KaringDataJson.encode(map)))
        assertThrows(IllegalArgumentException::class.java) { KaringDataJson.encode(Double.NaN) }
    }
}
