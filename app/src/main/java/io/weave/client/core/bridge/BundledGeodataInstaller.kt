package io.weave.client.core.bridge

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Installs immutable, build-time audited Geo data without allowing the core to download files.
 */
object BundledGeodataInstaller {
    private val resources = listOf(
        Resource(
            assetPath = "geodata/GeoIP.dat",
            fileName = "GeoIP.dat",
            sha256 = "1e49d985b16d13f3407d43582af64e0431c76e204a97460e5a8f859537687d13",
        ),
        Resource(
            assetPath = "geodata/GeoSite.dat",
            fileName = "GeoSite.dat",
            sha256 = "b2c9500f8e3403126a99f47bd9a5bced435c04316823b914bab6d5ee639e8cb7",
        ),
    )

    fun install(context: Context, coreHome: File) {
        check(coreHome.mkdirs() || coreHome.isDirectory) {
            "无法创建 Mihomo 数据目录"
        }
        resources.forEach { resource ->
            val destination = File(coreHome, resource.fileName)
            if (destination.isFile && sha256(destination) == resource.sha256) {
                return@forEach
            }
            val pending = File(coreHome, "${resource.fileName}.pending")
            pending.delete()
            context.assets.open(resource.assetPath).use { input ->
                pending.outputStream().use(input::copyTo)
            }
            check(sha256(pending) == resource.sha256) {
                pending.delete()
                "随包 Geo 数据校验失败"
            }
            Files.move(
                pending.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class Resource(
        val assetPath: String,
        val fileName: String,
        val sha256: String,
    )
}
