package io.weave.client.core.diagnostics

import io.weave.client.core.engine.MihomoConfigAssembler
import io.weave.client.core.vpn.LanProxyAddresses
import java.net.InetAddress
import org.json.JSONObject

internal data class SharedDevice(
    val address: String,
    val connectionIds: List<String>,
    val uploaded: Long,
    val downloaded: Long,
    val exits: List<String>,
)

/** A view of authenticated remote connections on the shared listener. No destination is kept. */
internal object LanShareTopology {
    private const val MAX_CONNECTIONS = 1_000
    private const val MAX_EXITS = 4

    fun read(snapshot: JSONObject): List<SharedDevice> {
        val rows = snapshot.optJSONArray("connections") ?: return emptyList()
        val devices = linkedMapOf<String, Device>()
        val ids = hashSetOf<String>()
        val addresses = HashMap<String, String>()
        repeat(minOf(rows.length(), MAX_CONNECTIONS)) { index ->
            val row = rows.optJSONObject(index) ?: return@repeat
            val metadata = row.optJSONObject("metadata") ?: return@repeat
            if (metadata.optInt("inboundPort") != MihomoConfigAssembler.MIXED_PORT ||
                (metadata.opt("inboundUser") as? String).isNullOrBlank()
            ) return@repeat
            val source = metadata.optString("sourceIP")
            val address = addresses.getOrPut(source) {
                privateLiteral(source).orEmpty()
            }.takeIf(String::isNotEmpty) ?: return@repeat
            val id = row.optString("id")
            if (!CoreApi.ID.matches(id) || !ids.add(id)) return@repeat
            val device = devices.getOrPut(address) { Device() }
            device.ids.add(id)
            device.upload = addBytes(device.upload, row.optLong("upload"))
            device.download = addBytes(device.download, row.optLong("download"))
            val exit = row.optJSONArray("chains")?.optString(0).orEmpty().take(120)
            if (exit.isNotBlank() && device.exits.size < MAX_EXITS) device.exits.add(exit)
        }
        return devices.map { (address, device) ->
            SharedDevice(address, device.ids, device.upload, device.download, device.exits.toList())
        }.sortedBy(SharedDevice::address)
    }

    private fun privateLiteral(value: String): String? {
        if (value.length !in 3..45 || !value.all { it in "0123456789abcdefABCDEF:." } ||
            (':' !in value && value.count { it == '.' } != 3)
        ) return null
        val address = runCatching { InetAddress.getByName(value) }.getOrNull() ?: return null
        return address.hostAddress?.takeIf { LanProxyAddresses.displayable(address) }
    }

    private fun addBytes(total: Long, bytes: Long): Long =
        total + bytes.coerceIn(0L, Long.MAX_VALUE - total)

    private class Device {
        val ids = mutableListOf<String>()
        val exits = linkedSetOf<String>()
        var upload = 0L
        var download = 0L
    }
}
