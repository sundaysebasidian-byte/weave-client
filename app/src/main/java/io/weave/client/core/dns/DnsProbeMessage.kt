package io.weave.client.core.dns

import java.io.DataInputStream
import java.io.InputStream
import kotlin.random.Random

/** One conventional recursive query for the reserved test name. */
internal class DnsProbeMessage(ipv6: Boolean, transaction: Int = Random.nextInt(65536)) {
    private val type = if (ipv6) 28 else 1
    val bytes = ByteArray(HEADER_BYTES + QUESTION_NAME.size + 4).apply {
        this[0] = (transaction ushr 8).toByte()
        this[1] = transaction.toByte()
        this[2] = 1 // Recursion desired.
        this[5] = 1 // One question.
        QUESTION_NAME.copyInto(this, HEADER_BYTES)
        this[size - 3] = type.toByte()
        this[lastIndex] = 1 // Internet class.
    }

    fun readHttp(input: InputStream): Boolean {
        val buffer = ByteArray(MAX_REPLY_BYTES + 1)
        var size = 0
        while (size < buffer.size) {
            val count = input.read(buffer, size, buffer.size - size)
            if (count < 0) break
            if (count > 0) {
                size += count
            } else {
                val value = input.read()
                if (value < 0) break
                buffer[size++] = value.toByte()
            }
        }
        return size <= MAX_REPLY_BYTES && provesResolution(buffer.copyOf(size))
    }

    fun readTls(input: InputStream): Boolean {
        val stream = DataInputStream(input)
        val length = stream.readUnsignedShort()
        if (length !in bytes.size..MAX_REPLY_BYTES) return false
        return provesResolution(ByteArray(length).also(stream::readFully))
    }

    fun provesResolution(reply: ByteArray): Boolean {
        if (reply.size !in bytes.size..MAX_REPLY_BYTES ||
            reply[0] != bytes[0] || reply[1] != bytes[1] ||
            reply.u8(2) and 0xfa != 0x80 || reply.u8(3) and 0x0f != 0 ||
            reply.u16(4) != 1 || reply.u16(6) == 0
        ) return false

        for (index in HEADER_BYTES until bytes.size) {
            val value = reply.u8(index)
            val normalized = if (value in 65..90) value + 32 else value
            if (normalized != bytes.u8(index)) return false
        }

        var offset = bytes.size
        repeat(reply.u16(6)) {
            offset = endOfName(reply, offset) ?: return false
            if (offset + RECORD_HEADER_BYTES > reply.size) return false
            val answerType = reply.u16(offset)
            val recordClass = reply.u16(offset + 2)
            val length = reply.u16(offset + 8)
            offset += RECORD_HEADER_BYTES
            if (offset + length > reply.size) return false
            val addressSize = if (type == 28) 16 else 4
            if (answerType == type && recordClass == 1 && length == addressSize) {
                for (index in offset until offset + length) if (reply[index] != 0.toByte()) return true
            }
            offset += length
        }
        return false
    }

    private fun endOfName(reply: ByteArray, start: Int): Int? {
        var offset = start
        while (offset < reply.size && offset - start <= 254) {
            val label = reply.u8(offset++)
            if (label == 0) return offset
            if (label >= 0xc0) {
                return (offset + 1).takeIf {
                    offset < reply.size && (((label and 0x3f) shl 8) or reply.u8(offset)) in HEADER_BYTES until start
                }
            }
            if (label > 63 || offset + label > reply.size) return null
            offset += label
        }
        return null
    }

    private fun ByteArray.u8(offset: Int): Int = this[offset].toInt() and 0xff
    private fun ByteArray.u16(offset: Int): Int = (u8(offset) shl 8) or u8(offset + 1)

    private companion object {
        const val HEADER_BYTES = 12
        const val RECORD_HEADER_BYTES = 10
        const val MAX_REPLY_BYTES = 4096
        val QUESTION_NAME = byteArrayOf(7) + "example".encodeToByteArray() +
            byteArrayOf(3) + "com".encodeToByteArray() + byteArrayOf(0)
    }
}
