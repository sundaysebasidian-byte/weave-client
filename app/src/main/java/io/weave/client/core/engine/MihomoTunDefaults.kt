package io.weave.client.core.engine

import io.weave.client.domain.Ipv6Mode

/**
 * Android TUN defaults shared with the CMFA integration path.
 *
 * The Android VPN owns the routes and file descriptor. Mihomo only needs the stack choice and
 * the DNS destinations that should be intercepted inside that descriptor. Keeping these values
 * in one small, pure object makes it harder for the Java/Kotlin bridge and the native adapter to
 * drift apart during a kernel upgrade.
 */
internal object MihomoTunDefaults {
    /** CMFA's Android service uses the system stack by default. */
    const val STACK = "system"

    /** Catch app DNS packets sent to both IPv4 and IPv6 resolver addresses. */
    const val DNS_HIJACK = "0.0.0.0,::"

    /** IPv4-only mode must not install an IPv6 DNS hijack target. */
    const val DNS_HIJACK_IPV4_ONLY = "0.0.0.0"

    private val ipv4 = TunAddress("172.19.0.1", 30, "172.19.0.2", DNS_HIJACK_IPV4_ONLY)
    private val ipv6 = TunAddress("fdfe:dcba:9876::1", 126, "fdfe:dcba:9876::2", "::")
    private val ipv4Only = listOf(ipv4)
    private val ipv6Only = listOf(ipv6)
    private val dualStack = listOf(ipv4, ipv6)

    fun addresses(mode: Ipv6Mode): List<TunAddress> = when (mode) {
        Ipv6Mode.DUAL_STACK -> dualStack
        Ipv6Mode.IPV4_ONLY -> ipv4Only
        Ipv6Mode.IPV6_ONLY -> ipv6Only
    }

    data class TunAddress(val gateway: String, val prefix: Int, val dns: String, val defaultRoute: String)
}
