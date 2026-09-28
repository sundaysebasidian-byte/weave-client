package io.weave.client.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.weave.client.core.vpn.VpnRuntimeState
import io.weave.client.core.vpn.WeaveVpnService
import io.weave.client.domain.ConnectionState
import io.weave.client.policy.PolicyPackCodec
import io.weave.client.policy.PolicyPackStore
import io.weave.client.routing.LocalRouteRule
import io.weave.client.routing.LocalRouteRuleStore
import io.weave.client.routing.LocalRouteRuleValidator
import io.weave.client.routing.LocalRuleAction
import io.weave.client.routing.LocalRuleType
import io.weave.client.routing.RemoteRuleSetStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Traffic-rule features that only need "hot-reload a running tunnel": offline policy packs,
 * local domain/IP rules and remote rule sets. Split from AppViewModel so each concern has its
 * own state holder; status messages reach the shared snackbar through [StatusBus].
 */
class RoutingRulesViewModel(application: Application) : AndroidViewModel(application) {
    private val policyPackStore by lazy { PolicyPackStore(application) }
    private val localRouteRuleStore by lazy { LocalRouteRuleStore(application) }
    private val ruleSetStore by lazy { RemoteRuleSetStore(application) }

    private val mutablePolicyPackState = MutableStateFlow(PolicyPackState())
    val policyPackState = mutablePolicyPackState.asStateFlow()

    private val mutableLocalRouteRuleState = MutableStateFlow(LocalRouteRuleState())
    val localRouteRuleState = mutableLocalRouteRuleState.asStateFlow()

    init {
        reload()
        // A backup restore replaces these stores underneath us.
        viewModelScope.launch { LocalDataEvents.revision.drop(1).collect { reload() } }
    }

    private fun reload() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { probeSafely { policyPackStore.list() } }
                .onSuccess { mutablePolicyPackState.value = PolicyPackState(packs = it) }
            withContext(Dispatchers.IO) { probeSafely { localRouteRuleStore.list() } }
                .onSuccess { mutableLocalRouteRuleState.value = LocalRouteRuleState(rules = it) }
            withContext(Dispatchers.IO) { probeSafely { ruleSetStore.list() } }
                .onSuccess { sets -> mutableRuleSetState.update { it.copy(sets = sets) } }
        }
    }

    fun importPolicyPack(uri: Uri) {
        if (mutablePolicyPackState.value.running) return
        mutablePolicyPackState.value = mutablePolicyPackState.value.copy(running = true, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                probeSafely {
                    val raw = getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                        readBounded(it, MAX_POLICY_PACK_BYTES)
                    } ?: error("无法读取策略包")
                    val pack = PolicyPackCodec.decode(raw.toString(Charsets.UTF_8), uri.toString())
                    policyPackStore.save(pack)
                    pack to policyPackStore.list()
                }
            }
            result.onSuccess { (pack, packs) ->
                mutablePolicyPackState.value = PolicyPackState(
                    packs = packs,
                    message = "已导入策略包「${pack.name}」",
                )
                reloadIfConnected("策略包已导入，正在安全更新运行配置")
            }.onFailure { error ->
                mutablePolicyPackState.update { it.copy(running = false, error = "策略包导入失败") }
            }
        }
    }

    private fun readBounded(input: java.io.InputStream, maxBytes: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(minOf(maxBytes, 16 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) error("策略包超过 ${maxBytes / 1024} KiB 限制")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    fun setPolicyPackActive(id: String, active: Boolean) {
        mutatePolicyPack(if (active) "策略包已启用" else "策略包已停用", "策略包状态更新失败",
            "策略包状态已变更，正在安全更新运行配置") { policyPackStore.setActive(id, active) }
    }

    fun deletePolicyPack(id: String) {
        mutatePolicyPack("策略包已删除", "策略包删除失败", "策略包已删除，正在安全更新运行配置") {
            policyPackStore.delete(id)
        }
    }

    private fun mutatePolicyPack(message: String, failure: String, reloadMessage: String, mutation: () -> Unit) {
        if (mutablePolicyPackState.value.running) return
        mutablePolicyPackState.update { it.copy(running = true, error = null, message = null) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { probeSafely { mutation(); policyPackStore.list() } }
                .onSuccess { packs ->
                    mutablePolicyPackState.value = PolicyPackState(packs = packs, message = message)
                    reloadIfConnected(reloadMessage)
                }.onFailure {
                    mutablePolicyPackState.update { it.copy(running = false, error = failure) }
                }
        }
    }

    fun addLocalRouteRule(type: LocalRuleType, value: String, action: LocalRuleAction): Boolean {
        return runCatching {
            val rule = LocalRouteRule(type = type, value = value, action = action)
            val normalized = LocalRouteRuleValidator.normalize(rule)
            val next = localRouteRuleStore.list() + normalized
            localRouteRuleStore.save(next)
            mutableLocalRouteRuleState.value = LocalRouteRuleState(next)
            reloadIfConnected("本地路由规则已添加，正在安全更新运行配置")
        }.onFailure { error ->
            mutableLocalRouteRuleState.update { it.copy(error = error.message ?: "规则无效") }
        }.isSuccess
    }

    fun setLocalRouteRuleEnabled(id: String, enabled: Boolean) {
        runCatching {
            val next = localRouteRuleStore.list().map { rule ->
                if (rule.id == id) rule.copy(enabled = enabled) else rule
            }
            localRouteRuleStore.save(next)
            mutableLocalRouteRuleState.value = LocalRouteRuleState(next)
            reloadIfConnected("本地路由规则已更新，正在安全应用")
        }.onFailure { error ->
            mutableLocalRouteRuleState.update { it.copy(error = error.message ?: "规则更新失败") }
        }
    }

    fun deleteLocalRouteRule(id: String) {
        runCatching {
            val next = localRouteRuleStore.list().filterNot { it.id == id }
            localRouteRuleStore.save(next)
            mutableLocalRouteRuleState.value = LocalRouteRuleState(next)
            reloadIfConnected("本地路由规则已删除，正在安全应用")
        }.onFailure { error ->
            mutableLocalRouteRuleState.update { it.copy(error = error.message ?: "规则删除失败") }
        }
    }

    fun clearLocalRouteRuleError() {
        mutableLocalRouteRuleState.update { it.copy(error = null) }
    }


    // Remote rule sets -------------------------------------------------------------------

    private val mutableRuleSetState = MutableStateFlow(RuleSetState())
    val ruleSetState = mutableRuleSetState.asStateFlow()

    fun loadRuleSets() {
        viewModelScope.launch {
            val sets = withContext(Dispatchers.IO) { probeSafely { ruleSetStore.list() } }
            mutableRuleSetState.update { it.copy(sets = sets.getOrDefault(it.sets)) }
        }
    }

    /** Adds a new set or re-downloads an existing one, then hot-reloads a running tunnel. */
    fun saveRuleSet(candidate: io.weave.client.routing.RemoteRuleSet) {
        if (mutableRuleSetState.value.running) return
        mutableRuleSetState.update { it.copy(running = true, error = null) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { probeSafely { ruleSetStore.refresh(candidate); ruleSetStore.list() } }
                .onSuccess { sets ->
                    mutableRuleSetState.value = RuleSetState(sets = sets)
                    reloadIfConnected("正在应用远程规则集")
                }
                .onFailure { error ->
                    mutableRuleSetState.update { it.copy(running = false, error = error.message ?: "规则集更新失败") }
                }
        }
    }

    fun refreshAllRuleSets() {
        if (mutableRuleSetState.value.running) return
        mutableRuleSetState.update { it.copy(running = true, error = null) }
        viewModelScope.launch {
            var failures = 0
            val sets = withContext(Dispatchers.IO) {
                ruleSetStore.list().forEach { set ->
                    if (runCatching { ruleSetStore.refresh(set) }.isFailure) failures++
                }
                ruleSetStore.list()
            }
            mutableRuleSetState.value = RuleSetState(
                sets = sets,
                error = if (failures > 0) "有 $failures 个规则集更新失败，已保留旧版本" else null,
            )
            reloadIfConnected("正在应用远程规则集")
        }
    }

    fun setRuleSetEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            val sets = withContext(Dispatchers.IO) { ruleSetStore.setEnabled(id, enabled); ruleSetStore.list() }
            mutableRuleSetState.update { it.copy(sets = sets) }
            reloadIfConnected("正在应用远程规则集")
        }
    }

    fun deleteRuleSet(id: String) {
        viewModelScope.launch {
            val sets = withContext(Dispatchers.IO) { ruleSetStore.delete(id); ruleSetStore.list() }
            mutableRuleSetState.update { it.copy(sets = sets) }
            reloadIfConnected("正在应用远程规则集")
        }
    }

    private fun reloadIfConnected(message: String) {
        if (VpnRuntimeState.snapshot.value.state != ConnectionState.CONNECTED) return
        StatusBus.post(message)
        WeaveVpnService.reload(getApplication())
    }

    private suspend fun <T> probeSafely(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    private companion object {
        const val MAX_POLICY_PACK_BYTES = 512 * 1024
    }
}
