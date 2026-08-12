package io.weave.client.core.bridge

import android.content.Context
import android.os.Build
import androidx.annotation.Keep
import io.weave.client.BuildConfig
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

@Keep
fun interface NativeCompletion {
    fun complete(error: String?)
}

@Keep
interface NativeTunCallback {
    fun markSocket(fd: Int)
    fun querySocketUid(protocol: Int, source: String, target: String): Int
}

object NativeBridge {
    private val loaded = runCatching {
        System.loadLibrary("bridge")
    }
    @Volatile
    private var initialized = false

    val isAvailable: Boolean
        get() = loaded.isSuccess

    fun initialize(context: Context): Result<Unit> = synchronized(this) {
        loaded.mapCatching {
            if (!initialized) {
                val coreHome = File(context.filesDir, "mihomo").apply { mkdirs() }
                BundledGeodataInstaller.install(context, coreHome)
                nativeInit(coreHome.absolutePath, BuildConfig.VERSION_NAME, Build.VERSION.SDK_INT)
                initialized = true
            }
        }
    }

    suspend fun loadConfiguration(profileDirectory: String): Result<Unit> {
        loaded.getOrThrow()
        return suspendCancellableCoroutine { continuation ->
            nativeLoad(
                NativeCompletion { error ->
                    if (continuation.isActive) {
                        continuation.resume(
                            if (error == null) Result.success(Unit)
                            else Result.failure(NativeCoreException(error)),
                        )
                    }
                },
                profileDirectory,
            )
        }
    }

    suspend fun validateConfiguration(profileDirectory: String): Result<Unit> {
        loaded.getOrThrow()
        return suspendCancellableCoroutine { continuation ->
            nativeValidateConfiguration(
                NativeCompletion { error ->
                    if (continuation.isActive) {
                        continuation.resume(
                            if (error == null) Result.success(Unit)
                            else Result.failure(NativeCoreException(error)),
                        )
                    }
                },
                profileDirectory,
            )
        }
    }

    fun reset() = nativeReset()

    fun startTun(
        fd: Int,
        stack: String,
        gateway: String,
        portal: String,
        dns: String,
        callback: NativeTunCallback,
    ): Int = nativeStartTun(fd, stack, gateway, portal, dns, callback)

    fun stopTun() = nativeStopTun()

    fun notifyInstalledAppsChanged(apps: String) = nativeNotifyInstalledAppsChanged(apps)

    fun queryTraffic(total: Boolean): LongArray = nativeQueryTraffic(total)

    fun queryGroup(name: String): String? = nativeQueryGroup(name)

    suspend fun healthCheck(name: String): Result<Unit> {
        loaded.getOrThrow()
        return suspendCancellableCoroutine { continuation ->
            nativeHealthCheck(
                NativeCompletion { error ->
                    if (continuation.isActive) {
                        continuation.resume(
                            if (error == null) Result.success(Unit)
                            else Result.failure(NativeCoreException(error)),
                        )
                    }
                },
                name,
            )
        }
    }

    private external fun nativeInit(home: String, versionName: String, sdkVersion: Int)
    private external fun nativeReset()
    private external fun nativeLoad(completion: NativeCompletion, profileDirectory: String)
    private external fun nativeValidateConfiguration(
        completion: NativeCompletion,
        profileDirectory: String,
    )
    private external fun nativeStartTun(
        fd: Int,
        stack: String,
        gateway: String,
        portal: String,
        dns: String,
        callback: NativeTunCallback,
    ): Int
    private external fun nativeStopTun()
    private external fun nativeNotifyInstalledAppsChanged(apps: String)
    private external fun nativeQueryTraffic(total: Boolean): LongArray
    private external fun nativeQueryGroup(name: String): String?
    private external fun nativeHealthCheck(completion: NativeCompletion, name: String)
}

class NativeCoreException(message: String) : IllegalStateException(message)
