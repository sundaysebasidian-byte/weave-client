package io.weave.client.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.weave.client.domain.NodeDisplayName
import io.weave.client.domain.ProxyNode
import io.weave.client.domain.Subscription
import io.weave.client.routing.CustomGroupStrategy
import io.weave.client.routing.CustomProxyGroup
import io.weave.client.routing.LocalRuleAction
import io.weave.client.routing.NodeRef
import io.weave.client.routing.RemoteRuleSet
import io.weave.client.routing.RuleSetBehavior
import io.weave.client.transfer.BackupCodec
import java.text.DateFormat
import java.util.Date

// Remote rule sets ------------------------------------------------------------------------------

@Composable
internal fun RuleSetsScreen(
    state: RuleSetState,
    onSave: (RemoteRuleSet) -> Unit,
    onRefreshAll: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<RemoteRuleSet?>(null) }
    var creating by remember { mutableStateOf(false) }
    WeaveToolScreen(
        title = "远程规则集",
        subtitle = "规则只在你手动添加或刷新时下载；每条都会先校验",
        onDismiss = onDismiss,
        actions = {
            IconButton(onClick = onRefreshAll, enabled = !state.running && state.sets.isNotEmpty()) {
                Icon(Icons.Rounded.Refresh, localizedContentDescription("全部刷新"))
            }
            IconButton(onClick = { creating = true }, enabled = !state.running) {
                Icon(Icons.Rounded.Add, localizedContentDescription("添加规则集"))
            }
        },
    ) {
        if (state.running) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("正在下载并校验", fontSize = 13.sp)
            }
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.sets.isEmpty()) {
                item {
                    EmptyState(
                        title = "还没有规则集",
                        body = "添加 HTTPS 地址的域名列表、IP 网段列表或经典规则，选择直连、代理或阻止。应用规则仍然优先。",
                    )
                }
            }
            items(state.sets, key = { it.id }) { set ->
                val language = LocalWeaveLanguage.current
                LiquidGlassPanel(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(), onClick = { editing = set }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(set.name, fontWeight = FontWeight.SemiBold, translate = false)
                            Text(
                                listOf(set.behavior.label, set.action.label, "${set.entryCount} 条").joinToString(" · ") {
                                    localizeWeaveText(it, language)
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                            set.sha256?.let {
                                Text("SHA-256 ${it.take(16)}…", fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, translate = false)
                            }
                            set.updatedAtMillis?.let {
                                Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)),
                                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, translate = false)
                            }
                        }
                        Switch(checked = set.enabled, onCheckedChange = { onToggle(set.id, it) })
                    }
                }
            }
        }
    }
    if (creating || editing != null) {
        RuleSetEditorDialog(
            initial = editing,
            running = state.running,
            onDismiss = { creating = false; editing = null },
            onSave = { onSave(it); creating = false; editing = null },
            onDelete = editing?.let { set -> { onDelete(set.id); editing = null } },
        )
    }
}

@Composable
private fun RuleSetEditorDialog(
    initial: RemoteRuleSet?,
    running: Boolean,
    onDismiss: () -> Unit,
    onSave: (RemoteRuleSet) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var url by remember { mutableStateOf(initial?.url.orEmpty()) }
    var behavior by remember { mutableStateOf(initial?.behavior ?: RuleSetBehavior.DOMAIN) }
    var action by remember { mutableStateOf(initial?.action ?: LocalRuleAction.DEFAULT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加规则集" else "编辑规则集", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, label = { Text("名称") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = url, onValueChange = { url = it.trim().take(2048) }, label = { Text("HTTPS 地址") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("格式", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RuleSetBehavior.entries.forEach { option ->
                        FilterChip(selected = behavior == option, onClick = { behavior = option }, label = { Text(option.label) })
                    }
                }
                Text("命中后", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LocalRuleAction.entries.forEach { option ->
                        FilterChip(selected = action == option, onClick = { action = option }, label = { Text(option.label) })
                    }
                }
                Text(
                    "支持每行一条的文本列表或 YAML payload；二进制 .mrs 格式不受支持。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !running && name.isNotBlank() && url.startsWith("https://", ignoreCase = true),
                onClick = {
                    onSave(
                        (initial ?: RemoteRuleSet(name = name, url = url, behavior = behavior, action = action))
                            .copy(name = name, url = url, behavior = behavior, action = action),
                    )
                },
            ) { Text("下载并保存") }
        },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text("删除", color = MaterialTheme.colorScheme.error) } }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

// Custom groups and chains -----------------------------------------------------------------------

@Composable
internal fun CustomGroupsScreen(
    groups: List<CustomProxyGroup>,
    subscriptions: List<Subscription>,
    nodes: List<ProxyNode>,
    error: String?,
    onSave: (CustomProxyGroup) -> Boolean,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<CustomProxyGroup?>(null) }
    var creating by remember { mutableStateOf(false) }
    val nodeNames = remember(nodes) { nodes.associate { NodeRef(it.subscriptionId, it.id) to NodeDisplayName.core(it.name) } }
    WeaveToolScreen(
        title = "自定义策略组",
        subtitle = "跨订阅组合节点；设置入口节点后成为链式代理",
        onDismiss = onDismiss,
        actions = {
            IconButton(onClick = { creating = true }, enabled = nodes.isNotEmpty()) {
                Icon(Icons.Rounded.Add, localizedContentDescription("新建策略组"))
            }
        },
    ) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp)) }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (groups.isEmpty()) {
                item {
                    EmptyState(
                        title = "还没有自定义策略组",
                        body = "新建后可在默认出口或应用分流中选择。链式代理的路径为：本机 → 入口节点 → 成员节点 → 目标网站。",
                    )
                }
            }
            items(groups, key = { it.id }) { group ->
                val language = LocalWeaveLanguage.current
                LiquidGlassPanel(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(), onClick = { editing = group }) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(group.name, fontWeight = FontWeight.SemiBold, translate = false)
                        Text(
                            listOf(group.strategy.label, "${group.members.size} 个节点").joinToString(" · ") {
                                localizeWeaveText(it, language)
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                        group.entry?.let { entry ->
                            Text(
                                "${localizeWeaveText("入口", language)} · ${nodeNames[entry] ?: "?"}",
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 12.sp,
                                translate = false,
                            )
                        }
                    }
                }
            }
        }
    }
    val target = editing
    if (creating || target != null) {
        CustomGroupEditor(
            initial = target,
            subscriptions = subscriptions,
            nodes = nodes,
            onDismiss = { creating = false; editing = null },
            onSave = { group -> if (onSave(group)) { creating = false; editing = null } },
            onDelete = target?.let { group -> { onDelete(group.id); editing = null } },
        )
    }
}

@Composable
private fun CustomGroupEditor(
    initial: CustomProxyGroup?,
    subscriptions: List<Subscription>,
    nodes: List<ProxyNode>,
    onDismiss: () -> Unit,
    onSave: (CustomProxyGroup) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var strategy by remember { mutableStateOf(initial?.strategy ?: CustomGroupStrategy.LOWEST_LATENCY) }
    var members by remember { mutableStateOf(initial?.members?.toSet().orEmpty()) }
    var entry by remember { mutableStateOf(initial?.entry) }
    var chained by remember { mutableStateOf(initial?.entry != null) }
    var query by remember { mutableStateOf("") }
    var pickingEntry by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val subscriptionNames = remember(subscriptions) { subscriptions.associate { it.id to it.name } }
    val filtered = remember(nodes, query) {
        val needle = query.trim()
        nodes.filter { needle.isEmpty() || it.name.contains(needle, true) }
    }
    WeaveToolScreen(
        title = if (initial == null) "新建策略组" else "编辑策略组",
        subtitle = "已选 ${members.size} 个节点",
        onDismiss = onDismiss,
        actions = {
            if (onDelete != null) {
                IconButton(onClick = { confirmingDelete = true }) {
                    Icon(Icons.Rounded.DeleteOutline, localizedContentDescription("删除"))
                }
            }
            TextButton(
                enabled = name.isNotBlank() && members.isNotEmpty() && (!chained || entry != null),
                onClick = {
                    onSave(
                        (initial ?: CustomProxyGroup(name = name, members = members.toList()))
                            .copy(name = name, strategy = strategy, members = members.toList(), entry = entry.takeIf { chained }),
                    )
                },
            ) { Text("保存") }
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("名称") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CustomGroupStrategy.entries.forEach { option ->
                        FilterChip(selected = strategy == option, onClick = { strategy = option }, label = { Text(option.label) })
                    }
                }
            }
            item {
                LiquidGlassPanel(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("链式代理", fontWeight = FontWeight.SemiBold)
                                Text("先连接入口节点，再由成员节点访问目标；延迟会叠加", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(checked = chained, onCheckedChange = { chained = it })
                        }
                        if (chained) {
                            OutlinedButton(onClick = { pickingEntry = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    entry?.let { ref -> nodes.firstOrNull { it.subscriptionId == ref.subscriptionId && it.id == ref.nodeId } }
                                        ?.let { NodeDisplayName.core(it.name) }
                                        ?: localizeWeaveText("选择入口节点", LocalWeaveLanguage.current),
                                    translate = false,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            item {
                OutlinedTextField(value = query, onValueChange = { query = it.take(100) }, label = { Text("搜索节点") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            filtered.groupBy { it.subscriptionId }.forEach { (subscriptionId, subscriptionNodes) ->
                item(key = "header.$subscriptionId") { TargetSectionLabel(subscriptionNames[subscriptionId] ?: "") }
                items(subscriptionNodes, key = { "${it.subscriptionId}/${it.id}" }) { node ->
                    val ref = NodeRef(node.subscriptionId, node.id)
                    val isEntry = chained && entry == ref
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isEntry) { members = if (ref in members) members - ref else members + ref }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = ref in members, onCheckedChange = null, enabled = !isEntry)
                        Spacer(Modifier.width(8.dp))
                        Text(NodeDisplayName.core(node.name), translate = false, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f))
                        Text(node.protocol, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, translate = false)
                    }
                }
            }
        }
    }
    if (confirmingDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("删除策略组？", fontWeight = FontWeight.Bold) },
            text = { Text("默认出口或应用分流若在使用它，将改为阻止联网，直到你重新选择出口。") },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDelete() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("取消") } },
        )
    }
    if (pickingEntry) {
        var entryQuery by remember { mutableStateOf("") }
        val entryCandidates = remember(nodes, entryQuery) {
            val needle = entryQuery.trim()
            nodes.filter { needle.isEmpty() || it.name.contains(needle, true) }
        }
        AlertDialog(
            onDismissRequest = { pickingEntry = false },
            title = { Text("选择入口节点", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = entryQuery, onValueChange = { entryQuery = it.take(100) },
                        label = { Text("搜索节点") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(entryCandidates, key = { "${it.subscriptionId}/${it.id}" }) { node ->
                            val ref = NodeRef(node.subscriptionId, node.id)
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    entry = ref
                                    members = members - ref
                                    pickingEntry = false
                                }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = entry == ref, onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(NodeDisplayName.core(node.name), translate = false, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(subscriptionNames[node.subscriptionId].orEmpty(), fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, translate = false)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingEntry = false }) { Text("取消") } },
        )
    }
}

// Backup ------------------------------------------------------------------------------------------

@Composable
internal fun BackupScreen(
    state: BackupState,
    onExport: (Uri, CharArray) -> Unit,
    onRead: (Uri, CharArray) -> Unit,
    onConfirmRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) onExport(uri, passphrase.toCharArray())
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onRead(uri, passphrase.toCharArray())
    }
    LaunchedEffect(state.message) { if (state.message != null) { passphrase = ""; confirmation = "" } }
    val longEnough = passphrase.length >= BackupCodec.MIN_PASSPHRASE_LENGTH
    WeaveToolScreen(
        title = "备份与恢复",
        subtitle = "PBKDF2 + AES-256-GCM 加密；Weave 无法找回忘记的密码",
        onDismiss = onDismiss,
        secure = true,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LiquidGlassPanel(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("备份密码", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it.take(128) },
                        label = { Text("至少 8 个字符") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it.take(128) },
                        label = { Text("再次输入（导出时）") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            enabled = !state.running && longEnough && passphrase == confirmation,
                            onClick = { exportLauncher.launch("weave-${System.currentTimeMillis() / 1000}.${BackupCodec.FILE_EXTENSION}") },
                            modifier = Modifier.weight(1f),
                        ) { Text("导出备份") }
                        OutlinedButton(
                            enabled = !state.running && longEnough,
                            onClick = { importLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f),
                        ) { Text("读取备份") }
                    }
                }
            }
            Text(
                "包含订阅、应用分流、默认出口、DNS 与路由设置、本地规则、远程规则集和自定义策略组。不包含离线策略包、系统 HTTP 代理和局域网共享设置。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            if (state.running) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp) }
            state.preview?.let { preview ->
                val language = LocalWeaveLanguage.current
                LiquidGlassPanel(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("备份内容", fontWeight = FontWeight.SemiBold)
                        Text(DateFormat.getDateTimeInstance().format(Date(preview.createdAtMillis)), fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, translate = false)
                        Text(
                            listOf("${preview.subscriptions} 个订阅", "${preview.routes} 条应用分流")
                                .joinToString(" · ") { localizeWeaveText(it, language) },
                            fontSize = 13.sp,
                            translate = false,
                        )
                        Text(
                            listOf("${preview.localRules} 条本地规则", "${preview.ruleSets} 个规则集", "${preview.groups} 个策略组")
                                .joinToString(" · ") { localizeWeaveText(it, language) },
                            fontSize = 13.sp,
                            translate = false,
                        )
                        Text(
                            "恢复会覆盖同一订阅和现有设置；同名订阅按安全审计原位更新。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                        Button(onClick = onConfirmRestore, enabled = !state.running, modifier = Modifier.fillMaxWidth()) {
                            Text("恢复")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EmptyState(title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(
            body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
