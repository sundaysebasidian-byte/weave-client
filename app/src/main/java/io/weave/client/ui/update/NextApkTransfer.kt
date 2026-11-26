package io.weave.client.ui.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.content.pm.PackageInfoCompat
import com.android.apksig.ApkVerifier
import io.weave.client.BuildConfig
import io.weave.client.lowerHex
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

internal class NextApkFailure : IOException("The Weave Next APK could not be verified.")

internal object NextApkTransfer {
    private const val applicationId = "io.weave.client.next"
    private const val releaseSigner = "db8942893e99bc290f7853685542876b62b0d8aca967db6aa1c40071d283320e"
    // GitHub's repository ID is stable across a rename; its asset CDN serves all public repos.
    private val assetPath = Regex(
        "/github-production-release-asset(?:-2e65be)?/1335945585/[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}",
    )
    private val network by lazy {
        OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).callTimeout(90, TimeUnit.SECONDS).build()
    }

    fun installedOfficialBuild(context: Context): Int? {
        if (!NextRelease.eligible(BuildConfig.BUILD_TYPE, context.packageName, "arm64-v8a" in Build.SUPPORTED_ABIS)) {
            return null
        }
        val info = try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(applicationId,
                if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
                else PackageManager.GET_SIGNATURES)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        val app = info.applicationInfo ?: return null
        if (app.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 || installedSigner(info) != releaseSigner ||
            !runCatching { isArm64Archive(File(app.sourceDir)) }.getOrDefault(false)
        ) return null
        val build = PackageInfoCompat.getLongVersionCode(info)
        return build.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
    }

    @Suppress("DEPRECATION")
    private fun installedSigner(info: PackageInfo): String? {
        val signers = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return signers?.singleOrNull()?.toByteArray()?.let { sha256().digest(it).lowerHex() }
    }

    suspend fun download(context: Context, release: NextRelease): File {
        var scratch: File? = null
        try {
            return withContext(Dispatchers.IO) { downloadArchive(context, release) { scratch = it } }
        } catch (failure: Exception) {
            withContext(NonCancellable + Dispatchers.IO) { scratch?.delete() }
            throw failure
        }
    }

    private suspend fun downloadArchive(context: Context, release: NextRelease, allocate: (File) -> Unit): File =
        coroutineScope {
            if (installedOfficialBuild(context)?.let { release.upgrades(BuildConfig.VERSION_NAME, it) } != true) {
                throw NextApkFailure()
            }

            val apk = File.createTempFile("next-update-", ".apk", context.cacheDir).also(allocate)
            val initial = release.url.toHttpUrlOrNull() ?: throw NextApkFailure()
            val downloadUrl = request(initial) { response ->
                if (response.code != 302 && response.code != 303 && response.code != 307) throw NextApkFailure()
                val location = response.header("Location")?.toHttpUrlOrNull() ?: throw NextApkFailure()
                if (!allowedAssetRedirect(location)) throw NextApkFailure()
                location
            }
            request(downloadUrl) { response ->
                if (response.code != 200 || response.header("Content-Encoding")?.lowercase()
                    ?.let { it != "identity" } == true
                ) throw NextApkFailure()
                val body = response.body
                if (body.contentLength() >= 0 && body.contentLength() != release.size) throw NextApkFailure()
                body.byteStream().use { input ->
                    apk.outputStream().use { output -> copyAndVerify(input, output, release) }
                }
            }
            verifyArchive(context, release, apk)
            currentCoroutineContext().ensureActive()
            apk
        }

    /**
     * Copies only to a new document selected with ACTION_CREATE_DOCUMENT, for people who would
     * rather install the APK from Files themselves.
     */
    suspend fun save(context: Context, release: NextRelease, apk: File, destination: Uri) =
        withContext(Dispatchers.IO) {
            if (!DocumentsContract.isDocumentUri(context, destination)) throw NextApkFailure()
            val resolver = context.contentResolver
            if (installedOfficialBuild(context)?.let { release.upgrades(BuildConfig.VERSION_NAME, it) } != true) {
                throw NextApkFailure()
            }
            verifyArchive(context, release, apk)
            // A document-provider URI is owned by the user. Refuse nonempty documents and
            // never delete the URI, even after a failed write, failed readback, or process loss.
            if (resolver.openInputStream(destination)?.use { it.read() } != -1) throw NextApkFailure()
            apk.inputStream().use { input ->
                resolver.openOutputStream(destination, "w")?.use { output ->
                    copyAndVerify(input, output, release)
                } ?: throw NextApkFailure()
            }
            resolver.openInputStream(destination)?.use { copyAndVerify(it, null, release) }
                ?: throw NextApkFailure()
        }

    /**
     * Hands a verified update to Android's package installer. Android shows its own confirmation
     * for every install; the result arrives at [NextInstallReceiver]. The archive is checked again
     * right before its bytes enter the session.
     */
    suspend fun install(context: Context, release: NextRelease, apk: File) = withContext(Dispatchers.IO) {
        if (installedOfficialBuild(context)?.let { release.upgrades(BuildConfig.VERSION_NAME, it) } != true) {
            throw NextApkFailure()
        }
        verifyArchive(context, release, apk)
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(applicationId)
            setSize(release.size)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            if (Build.VERSION.SDK_INT >= 33) setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("weave-next.apk", 0, release.size).use { output ->
                        copyAndVerify(input, output, release)
                        session.fsync(output)
                    }
                }
                currentCoroutineContext().ensureActive()
                val result = Intent(context, NextInstallReceiver::class.java).setPackage(context.packageName)
                // The installer fills in the status extras, so the intent must stay mutable; it is
                // explicit and the receiver is not exported.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                session.commit(PendingIntent.getBroadcast(context, id, result, flags).intentSender)
            }
        } catch (failure: Exception) {
            runCatching { installer.abandonSession(id) }
            throw failure
        }
        id
    }

    internal fun allowedAssetRedirect(url: HttpUrl): Boolean =
        url.isHttps && url.host == "release-assets.githubusercontent.com" && url.port == 443 &&
            url.username.isEmpty() && url.password.isEmpty() && url.fragment == null &&
            assetPath.matches(url.encodedPath)

    private suspend fun <T> request(url: HttpUrl, read: suspend (okhttp3.Response) -> T): T =
        coroutineScope {
            val call = network.newCall(Request.Builder().url(url).header("Accept-Encoding", "identity")
                .header("User-Agent", "Weave-Release-Check").build())
            val cancelCall = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    call.cancel()
                }
            }
            try {
                call.execute().use { read(it) }
            } finally {
                cancelCall.cancel()
            }
        }

    private suspend fun copyAndVerify(input: InputStream, output: OutputStream?, release: NextRelease) {
        val digest = sha256()
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > release.size || total > NextRelease.MAX_BYTES) throw NextApkFailure()
            digest.update(buffer, 0, count)
            output?.write(buffer, 0, count)
        }
        if (total != release.size || digest.digest().lowerHex() != release.digest) throw NextApkFailure()
    }

    internal fun verifyArchive(context: Context, release: NextRelease, apk: File) {
        // PackageManager archive metadata is not a cryptographic verification of the APK bytes.
        val verification = try {
            ApkVerifier.Builder(apk).setMinCheckedPlatformVersion(26).build().verify()
        } catch (_: com.android.apksig.apk.ApkFormatException) {
            throw NextApkFailure()
        } catch (_: java.security.NoSuchAlgorithmException) {
            throw NextApkFailure()
        }
        if (!verification.isVerified || (!verification.isVerifiedUsingV2Scheme &&
                !verification.isVerifiedUsingV3Scheme && !verification.isVerifiedUsingV31Scheme) ||
            verification.signerCertificates.singleOrNull()?.let { sha256().digest(it.encoded).lowerHex() } != releaseSigner
        ) throw NextApkFailure()

        @Suppress("DEPRECATION")
        val packageInfo = context.packageManager.getPackageArchiveInfo(apk.path, 0) ?: throw NextApkFailure()
        if (packageInfo.packageName != applicationId || packageInfo.versionName != release.version ||
            PackageInfoCompat.getLongVersionCode(packageInfo) != release.build.toLong() ||
            packageInfo.applicationInfo?.let { it.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0 } != true
        ) throw NextApkFailure()

        if (!isArm64Archive(apk)) throw NextApkFailure()
    }

    private fun isArm64Archive(apk: File): Boolean = ZipFile(apk).use { zip ->
        val entries = zip.entries()
        var bridge = false
        var core = false
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val name = entry.name
            if (entry.isDirectory || !name.startsWith("lib/")) continue
            if (!name.startsWith("lib/arm64-v8a/")) return@use false
            if (name == "lib/arm64-v8a/libbridge.so") bridge = true
            if (name == "lib/arm64-v8a/libclash.so") core = true
        }
        bridge && core
    }

    private fun sha256() = MessageDigest.getInstance("SHA-256")
}
