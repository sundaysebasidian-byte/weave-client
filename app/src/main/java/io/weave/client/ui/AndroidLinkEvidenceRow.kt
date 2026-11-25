package io.weave.client.ui

import android.net.ConnectivityManager
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.weave.client.R
import io.weave.client.core.diagnostics.AndroidLinkEvidence
import io.weave.client.core.diagnostics.readAndroidLinkEvidence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Queried on opening/refresh, not on a timer. Nothing leaves the device. */
@Composable
internal fun AndroidLinkEvidenceRow(localSnapshotAt: Long, probeAt: Long?) {
    key(localSnapshotAt, probeAt) {
        val context = LocalContext.current
        val link by produceState<AndroidLinkEvidence?>(null, context) {
            value = withContext(Dispatchers.IO) {
                readAndroidLinkEvidence(context.getSystemService(ConnectivityManager::class.java))
            }
        }
        val yes = stringResource(R.string.net_seen)
        val no = stringResource(R.string.net_not_seen)
        fun label(value: Boolean?) = when (value) { true -> yes; false -> no; null -> "—" }
        Column {
            Text(stringResource(R.string.net_link_snapshot), style = MaterialTheme.typography.titleSmall)
            Text("VPN: ${label(link?.vpnTransport)}  ·  IPv4: ${label(link?.ipv4Address)}  ·  IPv6: ${label(link?.ipv6Address)}")
            Text(stringResource(R.string.net_dns_count, link?.dnsServerCount?.toString() ?: "—"))
            Text(stringResource(R.string.net_link_scope), style = MaterialTheme.typography.bodySmall)
        }
    }
}
