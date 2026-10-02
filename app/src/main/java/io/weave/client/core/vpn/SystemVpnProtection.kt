package io.weave.client.core.vpn

import android.net.VpnService
import android.os.Build
import java.lang.ref.WeakReference

/** Read on demand: no timers, privileged settings access, or retained service instance. */
object SystemVpnProtection {
    @Volatile private var service = WeakReference<VpnService>(null)

    fun attach(value: VpnService) { service = WeakReference(value) }
    fun detach(value: VpnService) {
        if (service.get() === value) service.clear()
    }

    fun lockdownEnabled(): Boolean? {
        if (Build.VERSION.SDK_INT < 29) return null
        val current = service.get() ?: return null
        return runCatching { current.isAlwaysOn && current.isLockdownEnabled }.getOrNull()
    }
}
