package io.weave.client.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.weave.client.domain.ProxyChainSelection
import io.weave.client.domain.ProxyNode
import io.weave.client.domain.Subscription
import io.weave.client.domain.NodeDisplayName

@Composable
internal fun ProxyChainDialog(
    selection: ProxyChainSelection?,
    subscriptions: List<Subscription>,
    nodes: List<ProxyNode>,
    onSelect: (ProxyChainSelection?) -> Boolean,
    onDismiss: () -> Unit,
) {
    var entry by remember(selection) {
        mutableStateOf(nodes.firstOrNull {
            it.subscriptionId == selection?.entrySubscriptionId && it.id == selection.entryNodeId
        })
    }
    var exit by remember(selection) {
        mutableStateOf(nodes.firstOrNull {
            it.subscriptionId == selection?.exitSubscriptionId && it.id == selection.exitNodeId
        })
    }
    var choosingEntry by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var useAsDefault by remember(selection) { mutableStateOf(selection?.useAsDefault ?: true) }
    val subscriptionNames = remember(subscriptions) { subscriptions.associate { it.id to it.name } }
    val candidates = remember(nodes, query, subscriptionNames) {
        nodes.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) ||
            subscriptionNames[it.subscriptionId].orEmpty().contains(query, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("链式代理") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("入口连接出口服务器；目标网站看到出口 IP。应用规则可单独选择链式。")
                Text("UDP 出口协议经由某些入口可能不兼容；请连接后运行网络检测。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("同时用于默认出口")
                    Switch(checked = useAsDefault, onCheckedChange = { useAsDefault = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { choosingEntry = true }) {
                        Column { Text("入口"); Text(entry?.name?.let(NodeDisplayName::core) ?: "请选择", translate = entry == null) }
                    }
                    TextButton(onClick = { choosingEntry = false }) {
                        Column { Text("出口"); Text(exit?.name?.let(NodeDisplayName::core) ?: "请选择", translate = exit == null) }
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索订阅或节点") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    items(candidates, key = { "${it.subscriptionId}:${it.id}" }) { node ->
                        Column(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (choosingEntry) {
                                    entry = node
                                    choosingEntry = false
                                } else exit = node
                                error = false
                            }.padding(vertical = 9.dp),
                        ) {
                            Text(NodeDisplayName.core(node.name), translate = false)
                            Text(subscriptionNames[node.subscriptionId].orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, translate = false)
                        }
                    }
                }
                if (error) Text("节点已失效或入口、出口相同，请重新选择", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = entry != null && exit != null, onClick = {
                val selected = ProxyChainSelection(
                    entrySubscriptionId = requireNotNull(entry).subscriptionId,
                    entryNodeId = requireNotNull(entry).id,
                    exitSubscriptionId = requireNotNull(exit).subscriptionId,
                    exitNodeId = requireNotNull(exit).id,
                    useAsDefault = useAsDefault,
                )
                if (onSelect(selected)) onDismiss() else error = true
            }) { Text("启用") }
        },
        dismissButton = {
            Row {
                if (selection != null) TextButton(onClick = {
                    if (onSelect(null)) onDismiss()
                }) { Text("关闭链式") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
