package io.weave.client.transfer

import java.util.Base64

/**
 * Selected subscriptions, not a profile backup. Each authenticated sub-<UUID>.json entry
 * is a JSON string containing one base64 WVLAN001 packet. No profile controls are accepted.
 */
internal object SubscriptionFileCodec {
    const val MAX_FILE_BYTES = 32 * 1024 * 1024
    private const val MAX_TOTAL = 20 * 1024 * 1024
    private val entryName = Regex("sub-([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})\\.json")

    fun encrypt(items: List<TransferSubscription>, password: CharArray): ByteArray {
        require(items.size in 1..64)
        val entries = linkedMapOf<String, String>()
        val identities = hashSetOf<Pair<String, String>>()
        var total = 0
        for (item in items) {
            validateItem(item, identities)
            val name = "sub-${item.id}.json"
            require(entryName.matches(name) && name !in entries)
            val packet = LanTransferCodec.encode(listOf(item))
            try {
                total += packet.size
                require(total <= MAX_TOTAL)
                entries[name] = "\"" + Base64.getEncoder().encodeToString(packet) + "\""
            } finally { packet.fill(0) }
        }
        return BackupCodec.encrypt(entries, password)
    }

    fun decrypt(bytes: ByteArray, password: CharArray): List<TransferSubscription> {
        require(bytes.size <= MAX_FILE_BYTES)
        val entries = BackupCodec.decrypt(bytes, password)
        var total = 0
        val identities = hashSetOf<Pair<String, String>>()
        return entries.map { (name, text) ->
            val id = requireNotNull(entryName.matchEntire(name)).groupValues[1]
            require(text.length in 3..7_100_000 && text.first() == '"' && text.last() == '"')
            val data = Base64.getDecoder().decode(text.substring(1, text.lastIndex))
            try {
                total += data.size
                require(total <= MAX_TOTAL)
                LanTransferCodec.decode(data).single().copy(id = id).also {
                    validateItem(it, identities)
                }
            } finally { data.fill(0) }
        }
    }

    private fun validateItem(item: TransferSubscription, identities: MutableSet<Pair<String, String>>) {
        // Legacy stores could clip the last character to whitespace. Keep an actual saved
        // name portable; the receiving store normalizes its trailing space on write.
        require(item.name.isNotBlank() && item.name.length <= 80)
        require(item.payload.isNotBlank())
        if (item.source.startsWith("https://", ignoreCase = true)) {
            require(identities.add(item.name.trim() to item.source))
        }
        // WVLAN001 validates UTF-8 at the codec boundary for both encoding and decoding.
    }
}
