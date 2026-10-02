package io.weave.client

import android.app.Application
import android.os.Build
import java.io.File

/** Weave runs its UI in the default process and the VPN runtime in [VPN_SUFFIX]. */
object WeaveProcess {
    const val VPN_SUFFIX = ":vpn"

    val name: String by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            runCatching {
                File("/proc/self/cmdline").readBytes()
                    .takeWhile { it != 0.toByte() }
                    .toByteArray()
                    .toString(Charsets.UTF_8)
            }.getOrDefault("")
        }
    }

    val isVpnProcess: Boolean get() = name.endsWith(VPN_SUFFIX)
}
