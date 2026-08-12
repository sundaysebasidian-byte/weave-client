package io.weave.client.transfer

import io.weave.client.subscription.SubscriptionImportException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.URI
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class TransferSubscription(
    val name: String,
    val source: String,
    val payload: String,
)

data class LanTransferLink(
    val host: String,
    val port: Int,
    val token: String,
    val key: ByteArray,
) {
    fun encode(): String {
        val encodedKey = Base64.getUrlEncoder().withoutPadding().encodeToString(key)
        return "weave://lan/v1/$token?host=$host&port=$port#$encodedKey"
    }

    companion object {
        fun parse(raw: String): LanTransferLink {
            val value = raw.trim()
            if (value.toByteArray().size > 2_048) fail("局域网链接过长")
            val uri = runCatching { URI(value) }.getOrElse {
                fail("这不是有效的 Weave 局域网链接")
            }
            if (uri.scheme?.lowercase() != "weave" || uri.host?.lowercase() != "lan") {
                fail("这不是有效的 Weave 局域网链接")
            }
            val path = uri.path.orEmpty().split('/').filter(String::isNotEmpty)
            if (path.size != 2 || path[0] != "v1") fail("不支持的传输协议版本")
            val token = path[1]
            if (!TOKEN.matches(token)) fail("传输 token 无效")
            val query = uri.rawQuery.orEmpty().split('&').mapNotNull {
                val split = it.split('=', limit = 2)
                split.takeIf { fields -> fields.size == 2 }?.let { fields ->
                    fields[0] to fields[1]
                }
            }.toMap()
            val host = query["host"]?.takeIf(PrivateIpv4::isAllowed)
                ?: fail("局域网地址无效")
            val port = query["port"]?.toIntOrNull()?.takeIf { it in 1..65535 }
                ?: fail("传输端口无效")
            val key = runCatching {
                Base64.getUrlDecoder().decode(uri.rawFragment.orEmpty())
            }.getOrNull()?.takeIf { it.size == KEY_BYTES } ?: fail("传输密钥无效")
            return LanTransferLink(host, port, token, key)
        }

        private val TOKEN = Regex("[0-9a-f]{32}")
    }
}

object LanTransferCodec {
    const val MAX_CIPHERTEXT_BYTES = 20 * 1024 * 1024 + 64
    private const val MAX_SUBSCRIPTIONS = 64
    private const val MAX_NAME_BYTES = 320
    private const val MAX_SOURCE_BYTES = 8 * 1024
    private const val MAX_PAYLOAD_BYTES = 5 * 1024 * 1024
    private const val MAX_PLAINTEXT_BYTES = 20 * 1024 * 1024
    private val PLAIN_MAGIC = "WVLAN001".toByteArray()
    private val ENCRYPTED_MAGIC = "WVENC001".toByteArray()
    private val AAD = "weave-lan-transfer-v1".toByteArray()
    private val random = SecureRandom()

    fun randomKey(): ByteArray = ByteArray(KEY_BYTES).also(random::nextBytes)

    fun randomToken(): String = ByteArray(16).also(random::nextBytes)
        .joinToString("") { "%02x".format(it) }

    fun encode(items: List<TransferSubscription>): ByteArray {
        if (items.isEmpty() || items.size > MAX_SUBSCRIPTIONS) {
            fail("请选择 1–$MAX_SUBSCRIPTIONS 个订阅")
        }
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.write(PLAIN_MAGIC)
            output.writeInt(items.size)
            items.forEach { item ->
                output.writeString(item.name, MAX_NAME_BYTES)
                output.writeString(item.source, MAX_SOURCE_BYTES)
                output.writeString(item.payload, MAX_PAYLOAD_BYTES)
                if (bytes.size() > MAX_PLAINTEXT_BYTES) fail("传输内容超过 20 MiB 限制")
            }
        }
        return bytes.toByteArray()
    }

    fun decode(data: ByteArray): List<TransferSubscription> {
        if (data.size > MAX_PLAINTEXT_BYTES) fail("传输内容过大")
        val input = DataInputStream(ByteArrayInputStream(data))
        if (!input.readExact(8).contentEquals(PLAIN_MAGIC)) fail("传输内容标识无效")
        val count = input.readInt()
        if (count !in 1..MAX_SUBSCRIPTIONS) fail("订阅数量无效")
        val result = List(count) {
            TransferSubscription(
                name = input.readString(MAX_NAME_BYTES),
                source = input.readString(MAX_SOURCE_BYTES),
                payload = input.readString(MAX_PAYLOAD_BYTES),
            )
        }
        if (input.available() != 0) fail("传输内容包含多余数据")
        return result
    }

    fun seal(plaintext: ByteArray, key: ByteArray): ByteArray {
        if (key.size != KEY_BYTES) fail("传输密钥长度无效")
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(AAD)
        return ENCRYPTED_MAGIC + nonce + cipher.doFinal(plaintext)
    }

    fun open(packet: ByteArray, key: ByteArray): ByteArray {
        if (
            packet.size > MAX_CIPHERTEXT_BYTES ||
            packet.size < ENCRYPTED_MAGIC.size + 12 + 16 ||
            !packet.copyOfRange(0, 8).contentEquals(ENCRYPTED_MAGIC) ||
            key.size != KEY_BYTES
        ) {
            fail("加密传输包无效")
        }
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(128, packet, 8, 12),
            )
            cipher.updateAAD(AAD)
            cipher.doFinal(packet, 20, packet.size - 20)
        }.getOrElse { fail("传输包认证失败或已被篡改") }
    }
}

object PrivateIpv4 {
    fun isAllowed(value: String): Boolean {
        val parts = value.split('.').mapNotNull(String::toIntOrNull)
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        val (a, b) = parts
        return a == 10 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254) ||
            a == 127
    }
}

private fun DataOutputStream.writeString(value: String, maxBytes: Int) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    if (bytes.size > maxBytes) fail("传输字段过大")
    writeInt(bytes.size)
    write(bytes)
}

private fun DataInputStream.readString(maxBytes: Int): String {
    val size = readInt()
    if (size !in 0..maxBytes) fail("传输字段过大")
    val bytes = readExact(size)
    val value = bytes.toString(Charsets.UTF_8)
    if (!value.toByteArray(Charsets.UTF_8).contentEquals(bytes)) fail("传输文本不是有效 UTF-8")
    return value
}

private fun DataInputStream.readExact(size: Int): ByteArray =
    ByteArray(size).also { readFully(it) }

private fun fail(message: String): Nothing = throw SubscriptionImportException(message)

private const val KEY_BYTES = 32
