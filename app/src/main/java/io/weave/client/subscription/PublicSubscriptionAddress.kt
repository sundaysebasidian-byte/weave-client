package io.weave.client.subscription

import java.net.Inet6Address
import java.net.InetAddress

internal object PublicSubscriptionAddress {
    fun isPublic(address: InetAddress): Boolean {
        val bytes = address.address.map { it.toInt() and 0xff }
        if (bytes.size == 4) return ipv4(bytes)
        if (bytes.size != 16 || (address is Inet6Address && address.scopeId != 0)) return false
        val mapped = bytes.take(10).all { it == 0 } && bytes[10] == 255 && bytes[11] == 255
        val nat64 = bytes.take(12) == listOf(0, 100, 255, 155, 0, 0, 0, 0, 0, 0, 0, 0)
        if (mapped || nat64) return ipv4(bytes.takeLast(4))
        if (bytes[0] and 0xe0 != 0x20) return false
        if (bytes[0] == 0x20 && bytes[1] == 2) return false
        if (bytes[0] == 0x20 && bytes[1] == 1 &&
            (bytes[2] < 2 || (bytes[2] == 0x0d && bytes[3] == 0xb8))) return false
        return !(bytes[0] == 0x3f && bytes[1] == 0xff && bytes[2] and 0xf0 == 0)
    }

    private fun ipv4(b: List<Int>): Boolean = !(b[0] == 0 || b[0] == 10 || b[0] == 127 || b[0] >= 224 ||
        (b[0] == 100 && b[1] in 64..127) || (b[0] == 169 && b[1] == 254) ||
        (b[0] == 172 && b[1] in 16..31) ||
        (b[0] == 192 && (b[1] == 168 || (b[1] == 0 && b[2] in listOf(0, 2)) || (b[1] == 88 && b[2] == 99))) ||
        (b[0] == 198 && (b[1] in 18..19 || (b[1] == 51 && b[2] == 100))) ||
        (b[0] == 203 && b[1] == 0 && b[2] == 113))
}
