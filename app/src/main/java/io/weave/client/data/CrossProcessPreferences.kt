package io.weave.client.data

import android.content.Context
import android.content.SharedPreferences
import android.util.AtomicFile
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

/**
 * Explicit cross-process snapshots and serialized durable edits of the existing XML files.
 * No Android per-process cache, no Binder payload limit, no plaintext migration of encrypted data.
 * Acquire a new snapshot for each read operation; edits merge only changed keys under the lock.
 */
internal fun Context.crossProcessPreferences(name: String): SharedPreferences =
    TransactionalPreferences(applicationContext, name)

private class TransactionalPreferences(context: Context, name: String) : SharedPreferences {
    private val directory = File(context.applicationInfo.dataDir, "shared_prefs")
    private val file = File(directory, "$name.xml")
    private val atomicFile = AtomicFile(file)
    init { require(name in STORES) { "Unknown settings store" } }
    @Volatile private var snapshot: Map<String, Any> = transaction { read() }

    private fun <T> transaction(block: () -> T): T = synchronized(LOCKS.getOrPut(file.path) { Any() }) {
        check(directory.isDirectory || directory.mkdirs()) { "Settings directory unavailable" }
        RandomAccessFile(File(directory, "${file.name}.weave.lock"), "rw").channel.use { channel ->
            channel.lock().use { block() }
        }
    }

    private fun read(): MutableMap<String, Any> {
        if (!file.exists() && !File(file.path + ".bak").exists()) return linkedMapOf()
        return atomicFile.openRead().use { input ->
            val parser = Xml.newPullParser().apply { setInput(input, "UTF-8") }
            val values = linkedMapOf<String, Any>()
            require(parser.nextTag() == XmlPullParser.START_TAG && parser.name == "map") { "Invalid settings file" }
            while (parser.nextTag() == XmlPullParser.START_TAG) {
                val key = requireNotNull(parser.getAttributeValue(null, "name"))
                when (parser.name) {
                    "string" -> values[key] = parser.nextText()
                    "set" -> {
                        val strings = linkedSetOf<String>()
                        while (parser.nextTag() == XmlPullParser.START_TAG) {
                            require(parser.name == "string"); strings.add(parser.nextText())
                        }
                        values[key] = strings
                    }
                    else -> {
                        val value = requireNotNull(parser.getAttributeValue(null, "value"))
                        values[key] = when (parser.name) {
                            "int" -> value.toInt()
                            "long" -> value.toLong()
                            "float" -> value.toFloat()
                            "boolean" -> value.toBooleanStrict()
                            else -> error("Unsupported settings type")
                        }
                        parser.nextTag()
                    }
                }
            }
            values
        }
    }

    private fun write(values: Map<String, Any>) {
        val output = atomicFile.startWrite()
        try {
            val serializer = Xml.newSerializer().apply { setOutput(output, "UTF-8") }
            serializer.startDocument("UTF-8", true)
            serializer.startTag(null, "map")
            values.forEach { (key, value) ->
                val tag = when (value) {
                    is String -> "string"
                    is Set<*> -> "set"
                    is Int -> "int"
                    is Long -> "long"
                    is Float -> "float"
                    is Boolean -> "boolean"
                    else -> error("Unsupported settings value")
                }
                serializer.startTag(null, tag).attribute(null, "name", key)
                when (value) {
                    is String -> serializer.text(value)
                    is Set<*> -> value.forEach { item ->
                        serializer.startTag(null, "string").text(item as String).endTag(null, "string")
                    }
                    else -> serializer.attribute(null, "value", value.toString())
                }
                serializer.endTag(null, tag)
            }
            serializer.endTag(null, "map")
            serializer.endDocument()
            atomicFile.finishWrite(output)
        } catch (failure: Throwable) {
            atomicFile.failWrite(output)
            throw failure
        }
    }

    override fun getAll(): MutableMap<String, *> = snapshot.mapValues { (_, value) ->
        if (value is Set<*>) value.toSet() else value
    }.toMutableMap()
    override fun contains(key: String) = snapshot.containsKey(key)
    override fun getString(key: String, defValue: String?) = snapshot[key]?.let { it as String } ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        (snapshot[key] as Set<String>?)?.toMutableSet() ?: defValues?.toMutableSet()
    override fun getInt(key: String, defValue: Int) = snapshot[key]?.let { it as Int } ?: defValue
    override fun getLong(key: String, defValue: Long) = snapshot[key]?.let { it as Long } ?: defValue
    override fun getFloat(key: String, defValue: Float) = snapshot[key]?.let { it as Float } ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = snapshot[key]?.let { it as Boolean } ?: defValue
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        throw UnsupportedOperationException("Acquire a fresh snapshot after an IPC update")
    }
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val changed = linkedMapOf<String, Any?>()
        private var clear = false
        override fun putString(key: String, value: String?): SharedPreferences.Editor { changed[key] = value; return this }
        override fun putStringSet(key: String, value: MutableSet<String>?): SharedPreferences.Editor { changed[key] = value?.toSet(); return this }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor { changed[key] = value; return this }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor { changed[key] = value; return this }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor { changed[key] = value; return this }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { changed[key] = value; return this }
        override fun remove(key: String): SharedPreferences.Editor { changed[key] = null; return this }
        override fun clear(): SharedPreferences.Editor { clear = true; return this }
        override fun commit(): Boolean = transaction {
            val current = if (clear) linkedMapOf() else read()
            changed.forEach { (key, value) -> if (value == null) current.remove(key) else current[key] = value }
            write(current)
            snapshot = current.toMap()
            true
        }
        override fun apply() { commit() }
    }

    private companion object {
        val LOCKS = ConcurrentHashMap<String, Any>()
        val STORES = setOf("runtime_settings_v1", "app_routes_v1", "recovery_vault_v1", "offline_policy_packs_v1", "encrypted_subscriptions_v1")
    }
}
