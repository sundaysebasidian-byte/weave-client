package io.weave.client.core.bridge

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.Keep
import io.weave.client.BuildConfig
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
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
    // Loading the shared objects maps the native core into the app process. Keep this lazy so
    // opening the settings/subscription UI does not pay the native memory cost before a VPN is
    // actually requested. The service still loads them synchronously at the validation boundary.
    //
    // Some OriginOS/Vivo releases and externally repackaged APKs fail to materialise a usable
    // nativeLibraryDir. Others are stricter about a DT_NEEDED dependency being loaded before the
    // JNI bridge. Do both checks at the actual load boundary, while keeping the lightweight
    // install probe free of native memory mapping.
    @Volatile
    private var loadAttempt: Result<Unit>? = null
    @Volatile
    private var initialized = false

    /** Reports the load state without triggering a native library load. */
    val isAvailable: Boolean
        get() = isLoaded

    val isLoaded: Boolean
        get() = loadAttempt?.isSuccess == true

    /**
     * Checks the split APK payload without loading the native engine into this process.
     *
     * The UI uses this as a lightweight install check. Actual loading and initialization remain
     * at the VPN start boundary, so browsing subscriptions does not map the 40+ MB core.
     */
    fun isInstalled(context: Context): Boolean {
        context.applicationInfo.nativeLibraryDir?.let { nativeDirectory ->
            if (
                File(nativeDirectory, "libbridge.so").isFile &&
                    File(nativeDirectory, "libclash.so").isFile
            ) {
                return true
            }
        }

        // An OEM/repacked install can expose an empty nativeLibraryDir even though both libraries
        // remain valid APK entries. Check every base/split APK because Play-style ABI delivery may
        // place the pair in a split.
        val apkPaths = buildList {
            add(context.applicationInfo.sourceDir)
            context.applicationInfo.splitSourceDirs?.let(::addAll)
        }
        return Build.SUPPORTED_ABIS.any { abi ->
            val bridgeEntry = "lib/$abi/libbridge.so"
            val coreEntry = "lib/$abi/libclash.so"
            apkPaths.any { apkPath ->
                runCatching {
                    ZipFile(apkPath).use { zip ->
                        zip.getEntry(bridgeEntry) != null && zip.getEntry(coreEntry) != null
                    }
                }.getOrDefault(false)
            }
        }
    }

    fun initialize(context: Context): Result<Unit> = synchronized(this) {
        ensureLoaded(context).mapCatching {
            if (!initialized) {
                val coreHome = File(context.filesDir, "mihomo").apply { mkdirs() }
                BundledGeodataInstaller.install(context, coreHome)
                nativeInit(coreHome.absolutePath, BuildConfig.VERSION_NAME, Build.VERSION.SDK_INT)
                initialized = true
            }
        }
    }

    suspend fun loadConfiguration(profileDirectory: String): Result<Unit> {
        requireLoaded()
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
        requireLoaded()
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
        requireLoaded()
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

    private fun requireLoaded() {
        check(loadAttempt?.isSuccess == true) {
            loadAttempt?.exceptionOrNull()?.message ?: "Mihomo 原生库未加载"
        }
    }

    private fun ensureLoaded(context: Context): Result<Unit> {
        loadAttempt?.let { return it }
        return synchronized(this) {
            loadAttempt ?: runCatching {
                loadNativeLibraries(context)
            }.also { loadAttempt = it }
        }
    }

    private fun loadNativeLibraries(context: Context) {
        val standardLoadFailure = runCatching {
            // libbridge.so declares libclash.so as DT_NEEDED. Loading the core first avoids a
            // linker-namespace difference on some Vivo/OriginOS builds.
            System.loadLibrary("clash")
            System.loadLibrary("bridge")
        }.exceptionOrNull()
        if (standardLoadFailure == null) return

        val nativeDirectory = context.applicationInfo.nativeLibraryDir
            ?.takeIf(String::isNotBlank)
            ?.let(::File)
        val extractedDirectory = if (
            nativeDirectory?.let {
                File(it, "libclash.so").isFile && File(it, "libbridge.so").isFile
            } == true
        ) {
            nativeDirectory
        } else {
            extractNativeLibraries(context)
        }
        val core = extractedDirectory?.let { File(it, "libclash.so") }
        val bridge = extractedDirectory?.let { File(it, "libbridge.so") }
        if (core?.isFile != true || bridge?.isFile != true) throw standardLoadFailure
        runCatching {
            // Absolute paths are a fallback for ROMs that expose the extracted directory but do
            // not resolve the sibling DT_NEEDED library through the normal namespace lookup.
            System.load(core.absolutePath)
            System.load(bridge.absolutePath)
        }.getOrElse { absoluteLoadFailure ->
            absoluteLoadFailure.addSuppressed(standardLoadFailure)
            throw absoluteLoadFailure
        }
    }

    /**
     * A few OEM package managers do not expose a usable linker namespace. Copy only the matching
     * ABI pair from the signed APK to app-private storage and load by absolute path. This is a
     * fallback, not the normal path, so the common case keeps the core unmapped until connect.
     */
    private fun extractNativeLibraries(context: Context): File? {
        val apkPaths = buildList {
            add(context.applicationInfo.sourceDir)
            context.applicationInfo.splitSourceDirs?.let(::addAll)
        }
        val abiAndApk = Build.SUPPORTED_ABIS.asSequence()
            .flatMap { abi ->
                apkPaths.asSequence().mapNotNull { apkPath ->
                    val entries = runCatching {
                        ZipFile(apkPath).use { zip ->
                            val core = zip.getEntry("lib/$abi/libclash.so")
                            val bridge = zip.getEntry("lib/$abi/libbridge.so")
                            if (core != null && bridge != null) apkPath else null
                        }
                    }.getOrNull()
                    entries?.let { abi to it }
                }
            }
            .firstOrNull()
            ?: return null

        val (abi, apkPath) = abiAndApk
        val destination = File(context.codeCacheDir, "weave-native/$abi")
        check(destination.mkdirs() || destination.isDirectory) {
            "无法创建 Mihomo 原生库临时目录"
        }
        ZipFile(apkPath).use { zip ->
            listOf("libclash.so", "libbridge.so").forEach { name ->
                val entry = checkNotNull(zip.getEntry("lib/$abi/$name"))
                val target = File(destination, name)
                if (target.isFile && target.length() == entry.size) return@forEach
                val pending = File(destination, "$name.pending")
                pending.delete()
                zip.getInputStream(entry).use { input ->
                    pending.outputStream().use { output -> input.copyTo(output) }
                }
                check(pending.length() == entry.size) { "Mihomo 原生库复制不完整：$name" }
                Files.move(
                    pending.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        }
        Log.i(LOG_TAG, "Loaded native libraries from app-private fallback path for ABI $abi")
        return destination
    }

    private const val LOG_TAG = "WeaveNativeBridge"

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
