package io.weave.client.core.engine

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
}
