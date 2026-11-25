package io.weave.client.ui.update

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in artifact gate on an isolated Android guest. Host stages the public release APK and a
 * separately test-key-signed copy under the target test app's cache; no private release key,
 * network, installed production app, or overrides of the production trust root are needed.
 */
class NextPublicApkEvidenceTest {
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val tag = "weave-next-v0.4.0-rc5"
    private val name = "Weave-Next-0.4.0-rc5-build111-arm64-v8a.apk"
    private val sha = "eff1820ab8af53d58a3fb58a86f600dc80cf8f47dad635efee527b8757d91d53"
    private val metadata get() = requireNotNull(NextRelease.parse(tag,false,true,"0.4.0-rc4",name,
        "sha256:$sha",20_064_628,"https://github.com/${NextRelease.REPO}/releases/download/$tag/$name"))

    @Test fun unsignedDebugOrDifferentSignerIsNotTrusted() {
        assertThrows(NextApkFailure::class.java) {
            NextApkTransfer.verifyArchive(ctx, metadata, File(ctx.applicationInfo.sourceDir))
        }
    }

    @Test fun verifiesExactRc5AndRejectsMutatedAndResignedArchives() {
        assumeTrue("Supply the two published-byte fixtures for this separate artifact gate.",
            InstrumentationRegistry.getArguments().getString("weavePublicApkGate") == "true")
        val rc5 = File(ctx.cacheDir,"weave-evidence-rc5.apk")
        val resigned = File(ctx.cacheDir,"weave-evidence-resigned.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        rc5.inputStream().use { input ->
            val b = ByteArray(65536)
            var n = input.read(b)
            while (n >= 0) { digest.update(b,0,n); n = input.read(b) }
        }
        assertEquals(sha,digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) })
        NextApkTransfer.verifyArchive(ctx,metadata,rc5)
        val mutated = File.createTempFile("weave-integrity-", ".apk", ctx.cacheDir)
        try {
            rc5.copyTo(mutated,overwrite = true)
            RandomAccessFile(mutated,"rw").use {
                it.seek(1_048_576); val b = it.readUnsignedByte(); it.seek(1_048_576); it.writeByte(b xor 1)
            }
            assertThrows(NextApkFailure::class.java) { NextApkTransfer.verifyArchive(ctx, metadata, mutated) }
            require(resigned.isFile && resigned.length() > 0) { "Missing independent test-key APK" }
            assertThrows(NextApkFailure::class.java) { NextApkTransfer.verifyArchive(ctx, metadata, resigned) }
        } finally { mutated.delete() }
    }
}
