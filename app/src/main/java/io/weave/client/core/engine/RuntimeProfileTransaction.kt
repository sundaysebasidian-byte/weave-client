package io.weave.client.core.engine

import java.io.File

/**
 * Keeps plaintext provider profiles only in app-private cache and only for the reload window.
 *
 * The active native core continues using its in-memory configuration while a candidate is parsed.
 * A failed candidate restores the byte-for-byte previous profile; a failed start can then restart
 * that profile without reading the user's newly edited settings.
 */
class RuntimeProfileTransaction(cacheDirectory: File) {
    private val activeDirectory = File(cacheDirectory, ACTIVE_DIRECTORY)
    private val transactionDirectory = File(cacheDirectory, TRANSACTION_DIRECTORY)
    private val rollbackDirectory = File(transactionDirectory, ROLLBACK_DIRECTORY)
    private val candidateDirectory = File(transactionDirectory, CANDIDATE_DIRECTORY)

    fun begin() {
        clean()
        require(File(activeDirectory, CONFIG_FILE).isFile) {
            "没有可回退的运行配置"
        }
        copyDirectory(activeDirectory, rollbackDirectory)
    }

    fun captureCandidate() {
        require(File(activeDirectory, CONFIG_FILE).isFile) {
            "候选配置尚未完成"
        }
        candidateDirectory.deleteRecursively()
        copyDirectory(activeDirectory, candidateDirectory)
    }

    fun restoreCandidate() = replaceActiveWith(candidateDirectory)

    fun restoreRollback() = replaceActiveWith(rollbackDirectory)

    fun readActiveConfig(): String =
        File(activeDirectory, CONFIG_FILE).readText(Charsets.UTF_8)

    fun clean() {
        transactionDirectory.deleteRecursively()
    }

    private fun replaceActiveWith(source: File) {
        require(File(source, CONFIG_FILE).isFile) {
            "事务快照不完整"
        }
        activeDirectory.deleteRecursively()
        copyDirectory(source, activeDirectory)
    }

    private fun copyDirectory(source: File, destination: File) {
        check(source.copyRecursively(destination, overwrite = true)) {
            "无法复制运行配置"
        }
    }

    private companion object {
        const val ACTIVE_DIRECTORY = "mihomo-runtime"
        const val TRANSACTION_DIRECTORY = "mihomo-transaction"
        const val ROLLBACK_DIRECTORY = "rollback"
        const val CANDIDATE_DIRECTORY = "candidate"
        const val CONFIG_FILE = "config.yaml"
    }
}
