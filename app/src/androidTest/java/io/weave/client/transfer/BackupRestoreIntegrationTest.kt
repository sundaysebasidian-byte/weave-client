package io.weave.client.transfer

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.data.RuntimeSettingsStore
import io.weave.client.data.crossProcessPreferences
import io.weave.client.domain.RoutingMode
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.RouteKind
import io.weave.client.policy.*
import io.weave.client.routing.*
import io.weave.client.subscription.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID

/** Real Keystore/files in a disposable data root; no network, VPN or system settings. */
class BackupRestoreIntegrationTest {
    private class Fixture(val context: Context) {
        val store = SubscriptionSecretStore(context)
        val repository = SubscriptionRepository(context, store = store)
        val backup = WeaveBackup(context, repository)
        val rules = RemoteRuleSetStore(context)
        val old = store.save("Original", "local://file", payload(), SubscriptionPayloadParser().parse(payload()))
        val group = CustomProxyGroup(name = "Original group", members = listOf(NodeRef(old.id, old.nodes.single().id)))
        val rule = LocalRouteRule(type = LocalRuleType.DOMAIN, value = "old.example", action = LocalRuleAction.REJECT)
        init {
            CustomProxyGroupStore(context).save(listOf(group))
            LocalRouteRuleStore(context).save(listOf(rule))
            RuntimeSettingsStore(context).setRoutingMode(RoutingMode.RULE)
        }
        fun entries(id: String = old.id) = mutableMapOf(
            "manifest.json" to """{"format":"weave-backup","version":1,"createdAt":1}""",
            "subscriptions.json" to JSONArray().put(JSONObject().put("id", id).put("name", "Updated")
                .put("source", "local://file").put("payload", payload(1081))).toString(),
            "custom-groups.json" to "[]", "local-rules.json" to "[]", "rule-sets.json" to "[]", "routes.json" to "[]",
            "settings.json" to """{"routingMode":"DIRECT","customDns":"","defaultTarget":null}""",
        )
        fun fingerprint(): Map<String, String> = context.noBackupFilesDir.walkTopDown().filter { it.isFile }
            .filterNot { it.name.endsWith(".lock") || "profile-restore." in it.relativeTo(context.noBackupFilesDir).path }
            .associate { it.relativeTo(context.noBackupFilesDir).path to
                MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString("") { byte -> "%02x".format(byte) } }
    }

    private fun isolated(block: (Fixture) -> Unit) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(app.cacheDir.toPath(), "backup-test-").toFile()
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = root
            override fun getApplicationInfo() = ApplicationInfo(app.applicationInfo).apply { dataDir = root.absolutePath }
        }
        try { block(Fixture(context)) } finally { root.deleteRecursively() }
    }

    @Test fun laterRuleSetFailurePreservesEveryOriginalByte() = isolated { f -> runBlocking {
        repeat(RuleSetParser.MAX_SETS) { index ->
            f.rules.restore(RemoteRuleSet(name = "Old $index", url = "https://rules.example/list", behavior = RuleSetBehavior.DOMAIN,
                action = LocalRuleAction.REJECT), listOf("old$index.example"))
        }
        val entries = f.entries().apply { put("rule-sets.json", JSONArray().put(JSONObject().put("id", UUID.randomUUID().toString())
            .put("name", "New rules").put("url", "https://rules.example/new").put("behavior", "DOMAIN").put("action", "REJECT")
            .put("enabled", true).put("entries", JSONArray().put("new.example"))).toString()) }
        val before = f.fingerprint()
        val failure = runCatching { f.backup.restore(entries) }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("最多添加"))
        assertEquals(before, f.fingerprint())
        assertEquals(f.old, f.store.get(f.old.id))
    } }

    @Test fun malformedRouteFailsWithoutApplyingEarlierCategories() = isolated { f -> runBlocking {
        val before = f.fingerprint()
        val entries = f.entries().apply { put("routes.json", """[{"package":"example.app","name":"Example","target":{"kind":"UNKNOWN"}}]""") }
        assertTrue(runCatching { f.backup.restore(entries) }.isFailure)
        assertEquals(before, f.fingerprint())
    } }

    @Test fun unsupportedVersionDoesNotChangeExistingData() = isolated { f -> runBlocking {
        val before = f.fingerprint()
        val entries = f.entries().apply { put("manifest.json", """{"format":"weave-backup","version":99,"createdAt":1}""") }
        assertTrue(runCatching { f.backup.restore(entries) }.isFailure)
        assertEquals(before, f.fingerprint())
    } }

    @Test fun invalidSettingsDoNotSilentlyBecomeDefaults() = isolated { f -> runBlocking {
        val before = f.fingerprint()
        val entries = f.entries().apply { put("settings.json", """{"routingMode":"UNKNOWN","autoUpdateHours":7}""") }
        assertTrue(runCatching { f.backup.restore(entries) }.isFailure)
        assertEquals(before, f.fingerprint())
    } }

    @Test fun missingCategoryDoesNotCreateAnApparentlyCompleteRestore() = isolated { f -> runBlocking {
        val before = f.fingerprint()
        val entries = f.entries().apply { remove("custom-groups.json") }
        assertTrue(runCatching { f.backup.restore(entries) }.isFailure)
        assertEquals(before, f.fingerprint())
    } }

    @Test fun absentReferencedNodeRollsBackAllData() = isolated { f -> runBlocking {
        val before = f.fingerprint()
        val entries = f.entries().apply { put("routes.json", JSONArray().put(JSONObject().put("package", "example.app").put("name", "Example")
            .put("target", JSONObject().put("kind", "FIXED").put("subscription", f.old.id).put("node", "missing-node"))).toString()) }
        assertTrue(runCatching { f.backup.restore(entries) }.isFailure)
        assertEquals(before, f.fingerprint())
    } }

    @Test fun successfulRestorePublishesAllCategoriesAndClearsExplicitEmptySettings() = isolated { f -> runBlocking {
        val settings = RuntimeSettingsStore(f.context)
        settings.setCustomDnsEndpoint("https://resolver.invalid/private-token")
        settings.setDefaultRouteTarget(RouteTarget(RouteKind.FIXED, "Original", f.old.id, f.old.nodes.single().id))
        settings.setSystemHttpProxy(true)
        settings.setLanSharing(true)
        f.backup.restore(f.entries())
        assertEquals("Updated", f.store.get(f.old.id)!!.name)
        assertEquals(SubscriptionPayloadParser().parse(payload(1081)), SubscriptionPayloadParser().parse(f.store.readPayload(f.old.id)))
        assertTrue(f.store.readPayload(f.old.id).contains("port: 1081"))
        assertTrue(CustomProxyGroupStore(f.context).list().isEmpty())
        assertTrue(LocalRouteRuleStore(f.context).list().isEmpty())
        assertEquals(RoutingMode.DIRECT, settings.routingMode())
        assertNull(settings.defaultRouteTarget())
        assertEquals("", settings.networkPreferences().customDnsEndpoint)
        assertFalse(settings.networkPreferences().systemHttpProxy)
        assertFalse(settings.networkPreferences().lanSharing)
        assertFalse(File(f.context.noBackupFilesDir, "profile-restore.active").exists())
    } }

    @Test fun matchingNameAndSourceCannotRemapBackupSubscriptionIdentity() = isolated { f -> runBlocking {
        val id = UUID.randomUUID().toString()
        val entries = f.entries(id)
        val subs = JSONArray(entries.getValue("subscriptions.json"))
        subs.getJSONObject(0).put("name", f.old.name)
        entries["subscriptions.json"] = subs.toString()
        entries["routes.json"] = JSONArray().put(JSONObject().put("package", "example.app").put("name", "Example")
            .put("target", JSONObject().put("kind", "FIXED").put("subscription", id).put("node", f.old.nodes.single().id))).toString()
        f.backup.restore(entries)
        assertEquals(2, f.store.list().size)
        assertNotNull(f.store.get(id))
        assertEquals(f.old, f.store.get(f.old.id))
        assertEquals(id, io.weave.client.data.AppRouteStore(f.context).load().single().target.subscriptionId)
    } }

    @Test fun changesAfterPreviewInvalidateEntireRestore() = isolated { f -> runBlocking {
        val plan = f.backup.prepareRestore(f.entries())
        RuntimeSettingsStore(f.context).setBlockUdpStun(true)
        val before = f.fingerprint()
        assertTrue(runCatching { f.backup.restore(plan) }.isFailure)
        assertEquals(before, f.fingerprint())
    } }

    @Test fun versionTwoEncryptedRoundTripIncludesOfflinePolicyPacks() = isolated { f -> runBlocking {
        val policies = PolicyPackStore(f.context)
        val pack = PolicyPack("fixture-policy", "Offline", 1, "", "local://file",
            listOf(PolicyRule(PolicyRuleType.DOMAIN, "offline.example", PolicyRuleAction.REJECT)), "",
            PolicyPackIntegrity.UNSIGNED_REVIEW, active = true)
        policies.save(pack)
        val passphrase = "fixture backup password".toCharArray()
        val bytes = f.backup.export(passphrase)
        assertEquals(2, bytes[4].toInt())
        val entries = BackupCodec.decrypt(bytes, passphrase)
        assertEquals(2, JSONObject(entries.getValue("manifest.json")).getInt("version"))
        assertEquals(1, f.backup.preview(entries).policyPacks)
        policies.setActive(pack.id, false)
        f.backup.restore(entries)
        assertTrue(policies.read(pack.id).active)
        assertEquals(pack.rules, policies.read(pack.id).rules)
    } }

    @Test fun corruptGroupCannotBeExportedAsAnEmptySuccessfulBackup() = isolated { f -> runBlocking {
        File(f.context.noBackupFilesDir, "custom-proxy-groups.enc").writeText("damaged ciphertext")
        assertTrue(runCatching { f.backup.export("fixture backup password".toCharArray()) }.isFailure)
    } }

    @Test fun malformedJsonErrorDoesNotExposePrivateSubscriptionText() = isolated { f ->
        val entries = f.entries().apply { put("subscriptions.json", "[{secret:https://private.invalid/token/PRIVATE_TOKEN") }
        val error = runCatching { f.backup.preview(entries) }.exceptionOrNull()
        assertTrue(error is BackupException)
        assertFalse(error!!.message.orEmpty().contains("PRIVATE_TOKEN"))
    }

    @Test fun rulesOnlyProfileCanBeBackedUpWithoutSelectingASubscription() = isolated { f -> runBlocking {
        f.store.delete(f.old.id)
        CustomProxyGroupStore(f.context).save(emptyList())
        val passphrase = "fixture backup password".toCharArray()
        val entries = BackupCodec.decrypt(f.backup.export(passphrase), passphrase)
        assertEquals(0, f.backup.preview(entries).subscriptions)
        assertEquals(1, f.backup.preview(entries).localRules)
        f.backup.restore(entries)
    } }

    @Test fun incompleteSubscriptionIndexCannotBeExportedAsACompleteBackup() = isolated { f -> runBlocking {
        f.context.crossProcessPreferences("encrypted_subscriptions_v1").edit().remove("subscription.${f.old.id}.name").commit()
        assertTrue(runCatching { f.backup.export("fixture backup password".toCharArray()) }.isFailure)
    } }

    @Test fun damagedPrivateDnsSettingCannotBecomeAnEmptySuccessfulExport() = isolated { f -> runBlocking {
        f.context.crossProcessPreferences("runtime_settings_v1").edit().putString("custom_dns_endpoint_encrypted", "damaged").commit()
        assertTrue(runCatching { f.backup.export("fixture backup password".toCharArray()) }.isFailure)
    } }

    @Test fun malformedSavedRouteCannotDisappearFromSuccessfulExport() = isolated { f -> runBlocking {
        f.context.crossProcessPreferences("app_routes_v1").edit().putStringSet("route.packages", setOf("example.app"))
            .putString("route.example.app.kind", "UNKNOWN").commit()
        assertTrue(runCatching { f.backup.export("fixture backup password".toCharArray()) }.isFailure)
    } }

    companion object {
        private fun payload(port: Int = 1080) = """proxies:
  - {name: node, type: socks5, server: fixture.invalid, port: $port}
"""
    }
}
