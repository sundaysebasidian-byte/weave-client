package io.weave.client.core.engine

import java.io.File
import java.nio.file.Files

/** CMFA takes a directory path. Credentials only need to be on disk during that native call. */
internal class NativeProfileFile(private val cacheDirectory: File) {
    private val directory = File(cacheDirectory, "mihomo-runtime")

    suspend fun <T> withProfile(config: String, operation: suspend (String) -> T): T {
        check(directory.isDirectory || directory.mkdirs()) { "无法创建运行配置目录" }
        // The native operation may finish after a coroutine timeout. Never reuse its path.
        val working = Files.createTempDirectory(directory.toPath(), "profile-").toFile()
        try {
            File(working, "config.yaml").writeText(config, Charsets.UTF_8)
            return operation(working.absolutePath)
        } finally {
            check(working.deleteRecursively()) { "无法清理临时运行配置" }
            directory.delete()
        }
    }

    fun cleanStale() {
        check(directory.deleteRecursively() &&
            File(cacheDirectory, "mihomo-transaction").deleteRecursively()
        ) { "无法清理旧运行配置" }
    }
}
