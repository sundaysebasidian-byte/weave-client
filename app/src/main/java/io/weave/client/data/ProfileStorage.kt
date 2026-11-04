package io.weave.client.data

import android.content.Context
import android.system.Os
import android.system.OsConstants
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/** One lock order for UI, workers and VPN: profile first, then individual stores. No suspension. */
internal object ProfileStorage {
    private val locks = ConcurrentHashMap<String, ReentrantLock>()

    inline fun <T> access(context: Context, block: () -> T): T = enter(context).use { block() }

    fun enter(context: Context): Closeable {
        val directory = context.applicationContext.noBackupFilesDir
        check(directory.isDirectory || directory.mkdirs())
        val file = File(directory, "profile.lock")
        val monitor = locks.getOrPut(file.canonicalPath) { ReentrantLock() }
        monitor.lock()
        var handle: RandomAccessFile? = null
        var lock: FileLock? = null
        try {
            if (monitor.holdCount == 1) {
                handle = RandomAccessFile(file, "rw")
                lock = handle.channel.lock()
                Journal(context).recover()
            }
            return Closeable {
                try { lock?.release() } finally {
                    try { handle?.close() } finally { monitor.unlock() }
                }
            }
        } catch (error: Throwable) {
            try { lock?.release() } finally {
                try { handle?.close() } finally { monitor.unlock() }
            }
            throw error
        }
    }

    fun fingerprint(context: Context): String = access(context) { Journal(context).fingerprint() }

    /** Preparation/network work happens before this call; a changed profile requires a new preview. */
    fun <T> restore(context: Context, expected: String, block: () -> T): T = access(context) {
        val journal = Journal(context)
        check(journal.fingerprint() == expected) { "本机配置已变化，请重新预览备份" }
        journal.prepare()
        val result: T
        try {
            result = block()
            journal.syncProfile()
        } catch (error: Throwable) {
            try { journal.recover() } catch (recovery: Throwable) {
                error.addSuppressed(recovery)
                throw IllegalStateException("恢复未完成，原数据恢复记录已保留；请释放存储空间后重试", error)
            }
            throw error
        }
        // A failed commit marker has an uncertain outcome. Leave the journal for the next access;
        // never run a rollback that could disagree with the marker observed after a process crash.
        journal.commit()
        result
    }

    private class Journal(context: Context) {
        // Android itself may alias /data/user/0 and /data/data. Resolve trusted roots once,
        // then reject links inside those roots rather than rejecting the platform's own alias.
        private val data = context.applicationContext.noBackupFilesDir.canonicalFile
        private val prefs = File(context.applicationContext.applicationInfo.dataDir, "shared_prefs").canonicalFile
        private val active = File(data, "profile-restore.active")
        private val preparing = File(data, "profile-restore.preparing")
        private val finished = File(data, "profile-restore.finished")
        private val roots = mapOf("data" to data, "prefs" to prefs)
        private val trees = setOf("subscriptions", "rule-sets", "offline-policies")
        private val singles = setOf("custom-proxy-groups.enc", "local-route-rules.enc")
        private val preferenceNames = setOf("runtime_settings_v1", "app_routes_v1", "encrypted_subscriptions_v1", "offline_policy_packs_v1")

        private fun allowed(path: String): Boolean {
            val parts = path.split('/')
            if (parts.any { it.isEmpty() || it == "." || it == ".." || '\\' in it }) return false
            return when (parts.first()) {
                "data" -> parts.size == 2 && (parts[1] in singles || singles.any { parts[1] == "$it.pending" }) ||
                    parts.size == 3 && parts[1] in trees && parts[2] != "store.lock"
                "prefs" -> parts.size == 2 && preferenceNames.any { name -> parts[1] in setOf("$name.xml", "$name.xml.bak", "$name.xml.new") }
                else -> false
            }
        }

        private fun target(path: String): File {
            check(allowed(path)) { "Invalid profile recovery path" }
            val root = roots.getValue(path.substringBefore('/'))
            val file = File(root, path.substringAfter('/'))
            check(file.canonicalPath == file.absolutePath) { "Profile storage contains a symbolic link" }
            return file
        }

        private fun inventory(): Map<String, File> = buildMap {
            for (name in singles) for (suffix in listOf("", ".pending")) {
                val path = "data/$name$suffix"
                val file = target(path)
                if (Files.exists(file.toPath(), NOFOLLOW_LINKS)) put(path, file)
            }
            for (name in preferenceNames) for (suffix in listOf("", ".bak", ".new")) {
                val path = "prefs/$name.xml$suffix"
                val file = target(path)
                if (Files.exists(file.toPath(), NOFOLLOW_LINKS)) put(path, file)
            }
            for (name in trees) {
                val dir = File(data, name)
                if (!Files.exists(dir.toPath(), NOFOLLOW_LINKS)) continue
                check(dir.isDirectory && dir.canonicalPath == dir.absolutePath)
                requireNotNull(dir.listFiles()).filterNot { it.name == "store.lock" }.forEach { file ->
                    put("data/$name/${file.name}", target("data/$name/${file.name}"))
                }
            }
        }.toSortedMap().also { files ->
            check(files.size <= 10_000 && files.values.all { Files.isRegularFile(it.toPath(), NOFOLLOW_LINKS) }) {
                "Profile storage cannot be snapshotted safely"
            }
        }

        fun fingerprint(): String {
            val digest = MessageDigest.getInstance("SHA-256")
            inventory().forEach { (path, file) ->
                digest.update(path.toByteArray(Charsets.UTF_8)); digest.update(0.toByte())
                digest.update(hash(file).toByteArray(Charsets.US_ASCII))
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun prepare() {
            check(!active.exists())
            // Incomplete staging has never modified the profile and can be safely regenerated.
            removeJournal(preparing)
            check(preparing.mkdir())
            val manifest = JSONArray()
            inventory().entries.forEachIndexed { index, (path, file) ->
                val copy = File(preparing, "$index.bin")
                copySynced(file, copy)
                manifest.put(JSONObject().put("path", path).put("hash", hash(copy)))
            }
            FileOutputStream(File(preparing, "manifest.json")).use { output ->
                output.write(JSONObject().put("version", 1).put("files", manifest).toString().toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            syncDirectory(preparing)
            move(preparing, active)
            syncDirectory(data)
        }

        fun recover() {
            // The finished name is the commit point; cleanup never changes live profile files.
            removeJournal(finished)
            if (!active.exists()) return
            check(active.isDirectory && active.canonicalPath == active.absolutePath)
            val metadata = JSONObject(File(active, "manifest.json").readText(Charsets.UTF_8))
            check(metadata.get("version") == 1) { "Unsupported profile recovery format" }
            val manifest = metadata.getJSONArray("files")
            check(manifest.length() <= 10_000)
            val copies = (0 until manifest.length()).associate { index ->
                val record = manifest.getJSONObject(index)
                val path = record.getString("path")
                val copy = File(active, "$index.bin")
                target(path)
                check(Files.isRegularFile(copy.toPath(), NOFOLLOW_LINKS) && hash(copy) == record.getString("hash")) {
                    "Profile recovery data is incomplete; originals have been retained"
                }
                path to copy
            }
            check(copies.size == manifest.length())
            val current = inventory()
            // Verify every recovery byte before touching the live profile. Repeating after a kill
            // is safe: the immutable journal remains until all old files and removals are durable.
            copies.entries.forEachIndexed { index, (path, source) ->
                val destination = target(path)
                check(destination.parentFile!!.isDirectory || destination.parentFile!!.mkdirs())
                val pending = File(active, "$index.restore")
                try { copySynced(source, pending); move(pending, destination) } finally { pending.delete() }
            }
            (current.keys - copies.keys).forEach { check(target(it).delete()) }
            syncProfile()
            move(active, finished)
            syncDirectory(data)
            removeJournal(finished)
        }

        fun syncProfile() {
            inventory().values.forEach { file -> RandomAccessFile(file, "rw").use { it.fd.sync() } }
            for (directory in listOf(data, prefs) + trees.map { File(data, it) }) {
                if (directory.isDirectory) syncDirectory(directory)
            }
        }

        fun commit() {
            move(active, finished)
            syncDirectory(data)
            removeJournal(finished)
        }

        private fun removeJournal(directory: File) {
            if (!directory.exists()) return
            check(directory.isDirectory && directory.canonicalPath == directory.absolutePath)
            check(requireNotNull(directory.listFiles()).all {
                Files.isRegularFile(it.toPath(), NOFOLLOW_LINKS) && (it.name == "manifest.json" || Regex("[0-9]+\\.(bin|restore)").matches(it.name))
            }) { "Unrecognized recovery files; retained for inspection" }
            check(directory.deleteRecursively()) { "Unable to remove completed profile recovery files" }
        }

        private fun copySynced(source: File, destination: File) {
            source.inputStream().use { input -> FileOutputStream(destination).use { output ->
                input.copyTo(output); output.fd.sync()
            } }
        }

        private fun hash(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private fun move(source: File, destination: File) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }

        private fun syncDirectory(directory: File) {
            val descriptor = Os.open(directory.path, OsConstants.O_RDONLY, 0)
            try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
        }
    }
}
