package io.weave.client.transfer

import io.weave.client.data.utf8SizeUpTo
import io.weave.client.subscription.SubscriptionImportException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class TransferSubscription(
    val name: String,
    val source: String,
    val payload: String,
    /** Local identity used by LAN Sync selection/merge. */
    val id: String = "",
)

data class LanTransferLink(
    val host: String,
    val port: Int,
    val token: String,
    val key: ByteArray,
) {
    override fun toString(): String = "LanTransferLink(redacted)"

    fun encode(): String {
        val encodedKey = Base64.getUrlEncoder().withoutPadding().encodeToString(key)
        return "weave://lan/v1/$token?host=$host&port=$port#$encodedKey"
    }

    /** Short out-of-band confirmation value; it authenticates that both devices show the same link. */
    fun confirmationCode(): String {
        val input = token.toByteArray(Charsets.US_ASCII) + key
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(input)
        val number = ((digest[0].toInt() and 0xff) shl 16) or
            ((digest[1].toInt() and 0xff) shl 8) or
            (digest[2].toInt() and 0xff)
        return (number % 1_000_000).toString().padStart(6, '0')
    }

    /** Check before consuming the single-use LAN response. */
    fun requireConfirmation(input: String) {
        val code = input.trim()
        check(code.length == 6 && code.all(Char::isDigit)) {
            "请输入发送设备显示的 6 位短码"
        }
        val normalized = ByteArray(code.length) { index -> ('0'.code + Character.digit(code[index], 10)).toByte() }
        check(java.security.MessageDigest.isEqual(normalized, confirmationCode().toByteArray(Charsets.US_ASCII))) {
            "短码不匹配：请让发送设备重新显示当前二维码和短码"
        }
    }

    companion object {
        fun parse(raw: String): LanTransferLink {
            if (raw.length > 2_048) fail("局域网链接过长")
            val value = raw.trim()
            if (value.any { it.code > 0x7f }) fail("这不是有效的 Weave 局域网链接")
            val uri = runCatching { URI(value) }.getOrElse {
                fail("这不是有效的 Weave 局域网链接")
            }
            if (!"weave".equals(uri.scheme, ignoreCase = true) || !"lan".equals(uri.host, ignoreCase = true) ||
                uri.rawUserInfo != null || uri.port != -1
            ) {
                fail("这不是有效的 Weave 局域网链接")
            }
            val rawPath = uri.rawPath.orEmpty()
            if (!rawPath.startsWith("/v1/")) fail("不支持的传输协议版本")
            val token = rawPath.removePrefix("/v1/")
            if (!TOKEN.matches(token)) fail("传输 token 无效")
            val query = HashMap<String, String>(2)
            for (field in uri.rawQuery.orEmpty().split('&')) {
                val separator = field.indexOf('=')
                if (separator <= 0) fail("这不是有效的 Weave 局域网链接")
                val name = field.substring(0, separator)
                if ((name != "host" && name != "port") || query.put(name, field.substring(separator + 1)) != null) {
                    fail("这不是有效的 Weave 局域网链接")
                }
            }
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
        .toHexString()

    fun encode(items: List<TransferSubscription>): ByteArray {
        if (items.isEmpty() || items.size > MAX_SUBSCRIPTIONS) {
            fail("请选择 1–$MAX_SUBSCRIPTIONS 个订阅")
        }
        val sizes = IntArray(items.size * 3)
        var next = 0
        var size = PLAIN_MAGIC.size + Int.SIZE_BYTES + sizes.size * Int.SIZE_BYTES
        fun measure(value: String, limit: Int) {
            val bytes = utf8SizeUpTo(value, limit) ?: fail("传输字段过大或不是有效 UTF-8")
            size += bytes
            if (size > MAX_PLAINTEXT_BYTES) fail("传输内容超过 20 MiB 限制")
            sizes[next++] = bytes
        }
        for (item in items) {
            measure(item.name, MAX_NAME_BYTES)
            measure(item.source, MAX_SOURCE_BYTES)
            measure(item.payload, MAX_PAYLOAD_BYTES)
        }
        val output = ByteBuffer.allocate(size).put(PLAIN_MAGIC).putInt(items.size)
        val encoder = Charsets.UTF_8.newEncoder()
        // An array-backed CharBuffer lets the encoder use its bulk ASCII/UTF-8 path.
        val chars = CharArray(8 * 1024)
        val scratch = CharBuffer.wrap(chars)
        next = 0
        fun write(value: String) {
            output.putInt(sizes[next++])
            encoder.reset()
            var offset = 0
            do {
                var end = minOf(value.length, offset + chars.size)
                if (end < value.length && value[end - 1].isHighSurrogate()) end--
                value.toCharArray(chars, 0, offset, end)
                scratch.clear().limit(end - offset)
                check(encoder.encode(scratch, output, end == value.length).isUnderflow)
                offset = end
            } while (offset < value.length)
            check(encoder.flush(output).isUnderflow)
        }
        for (item in items) {
            write(item.name)
            write(item.source)
            write(item.payload)
        }
        check(!output.hasRemaining())
        return output.array()
    }

    fun decode(data: ByteArray): List<TransferSubscription> {
        if (data.size > MAX_PLAINTEXT_BYTES) fail("传输内容过大")
        if (data.size < PLAIN_MAGIC.size + Int.SIZE_BYTES ||
            PLAIN_MAGIC.indices.any { data[it] != PLAIN_MAGIC[it] }
        ) fail("传输内容标识无效")
        val input = ByteBuffer.wrap(data)
        input.position(PLAIN_MAGIC.size)
        val count = input.int
        if (count !in 1..MAX_SUBSCRIPTIONS) fail("订阅数量无效")
        val utf8 = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val scratch = CharBuffer.allocate(minOf(4096, data.size))
        val result = List(count) {
            TransferSubscription(
                name = input.readString(MAX_NAME_BYTES, utf8, scratch),
                source = input.readString(MAX_SOURCE_BYTES, utf8, scratch),
                payload = input.readString(MAX_PAYLOAD_BYTES, utf8, scratch),
            )
        }
        if (input.hasRemaining()) fail("传输内容包含多余数据")
        return result
    }

    fun seal(plaintext: ByteArray, key: ByteArray): ByteArray {
        if (key.size != KEY_BYTES) fail("传输密钥长度无效")
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(AAD)
        val headerSize = ENCRYPTED_MAGIC.size + nonce.size
        val packet = ByteArray(headerSize + cipher.getOutputSize(plaintext.size))
        ENCRYPTED_MAGIC.copyInto(packet)
        nonce.copyInto(packet, ENCRYPTED_MAGIC.size)
        val written = cipher.doFinal(plaintext, 0, plaintext.size, packet, headerSize)
        check(written == packet.size - headerSize)
        return packet
    }

    fun open(packet: ByteArray, key: ByteArray): ByteArray {
        if (
            packet.size > MAX_CIPHERTEXT_BYTES ||
            packet.size < ENCRYPTED_MAGIC.size + 12 + 16 ||
            ENCRYPTED_MAGIC.indices.any { packet[it] != ENCRYPTED_MAGIC[it] } ||
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
        if (value.length !in 7..15) return false
        val parts = value.split('.')
        if (parts.size != 4 || parts.any {
                it.isEmpty() || (it.length > 1 && it[0] == '0') ||
                    it.any { digit -> digit !in '0'..'9' } || (it.toIntOrNull() ?: -1) !in 0..255
            }
        ) return false
        val a = parts[0].toInt()
        val b = parts[1].toInt()
        return a == 10 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168)
    }
}

private fun ByteBuffer.readString(maxBytes: Int, utf8: CharsetDecoder, scratch: CharBuffer): String {
    if (remaining() < Int.SIZE_BYTES) fail("传输内容标识无效")
    val size = int
    if (size !in 0..maxBytes) fail("传输字段过大")
    if (size > remaining()) fail("传输内容标识无效")
    val field = slice().apply { limit(size) }
    // Validate with a bounded reusable buffer. Create the String from the original input
    // only after validation, without a payload-sized byte or char array per field.
    utf8.reset()
    while (true) {
        scratch.clear()
        val result = utf8.decode(field, scratch, true)
        if (result.isError) fail("传输文本不是有效 UTF-8")
        if (result.isUnderflow) break
    }
    val offset = position()
    position(offset + size)
    return String(array(), arrayOffset() + offset, size, Charsets.UTF_8)
}

private fun fail(message: String): Nothing = throw SubscriptionImportException(message)

private const val KEY_BYTES = 32
