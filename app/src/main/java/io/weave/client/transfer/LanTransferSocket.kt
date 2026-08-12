package io.weave.client.transfer

import io.weave.client.subscription.SubscriptionImportException
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OneTimeLanTransferServer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: ServerSocket? = null
    private var job: Job? = null

    fun start(packet: ByteArray, expiryMs: Long = 5 * 60_000L): LanTransferLink {
        stop()
        val host = localPrivateIpv4()
            ?: throw SubscriptionImportException("未找到可用的局域网 IPv4 地址")
        val server = ServerSocket(0)
        val token = LanTransferCodec.randomToken()
        val key = LanTransferCodec.randomKey()
        val sealed = LanTransferCodec.seal(packet, key)
        val consumed = AtomicBoolean(false)
        socket = server
        job = scope.launch {
            launch {
                delay(expiryMs)
                stop()
            }
            while (!server.isClosed && !consumed.get()) {
                runCatching { server.accept() }.getOrNull()?.use { client ->
                    client.soTimeout = 5_000
                    val request = readHeader(client)
                    val allowed = request.startsWith("GET /v1/$token HTTP/1.") &&
                        consumed.compareAndSet(false, true)
                    if (allowed) {
                        val header = (
                            "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: application/vnd.weave.transfer\r\n" +
                                "Cache-Control: no-store\r\n" +
                                "Content-Length: ${sealed.size}\r\n" +
                                "Connection: close\r\n\r\n"
                            ).toByteArray()
                        client.getOutputStream().apply {
                            write(header)
                            write(sealed)
                            flush()
                        }
                    } else {
                        client.getOutputStream().apply {
                            write(
                                (
                                    "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n" +
                                        "Connection: close\r\n\r\n"
                                    ).toByteArray(),
                            )
                            flush()
                        }
                    }
                }
            }
            stop()
        }
        return LanTransferLink(host, server.localPort, token, key)
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
        job?.cancel()
        job = null
    }

    private fun readHeader(socket: Socket): String {
        val output = ByteArrayOutputStream()
        val input = socket.getInputStream()
        var matched = 0
        while (output.size() < 8_192) {
            val value = input.read()
            if (value < 0) break
            output.write(value)
            matched = when {
                matched == 0 && value == '\r'.code -> 1
                matched == 1 && value == '\n'.code -> 2
                matched == 2 && value == '\r'.code -> 3
                matched == 3 && value == '\n'.code -> 4
                else -> 0
            }
            if (matched == 4) break
        }
        return output.toString(Charsets.US_ASCII.name())
    }

    private fun localPrivateIpv4(): String? =
        NetworkInterface.getNetworkInterfaces().toList()
            .filter {
                it.isUp &&
                    !it.isLoopback &&
                    !it.isPointToPoint &&
                    !it.isVirtual &&
                    !it.name.startsWith("tun") &&
                    !it.name.startsWith("wg")
            }
            .sortedBy {
                when {
                    it.name.startsWith("wlan") -> 0
                    it.name.startsWith("eth") -> 1
                    else -> 2
                }
            }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress.orEmpty() }
            .firstOrNull(PrivateIpv4::isAllowed)
}

object LanTransferClient {
    suspend fun fetch(link: LanTransferLink): ByteArray = withContext(Dispatchers.IO) {
        Socket(link.host, link.port).use { socket ->
            socket.soTimeout = 10_000
            socket.getOutputStream().apply {
                write(
                    (
                        "GET /v1/${link.token} HTTP/1.1\r\nHost: ${link.host}\r\n" +
                            "Connection: close\r\n\r\n"
                        ).toByteArray(),
                )
                flush()
            }
            val all = socket.getInputStream().readBounded(
                LanTransferCodec.MAX_CIPHERTEXT_BYTES + 4_096,
            )
            val separator = "\r\n\r\n".toByteArray()
            val headerEnd = all.indexOf(separator)
            if (headerEnd < 0) throw SubscriptionImportException("发送设备返回无效响应")
            val header = all.copyOfRange(0, headerEnd).toString(Charsets.US_ASCII)
            if (!header.startsWith("HTTP/1.1 200 ")) {
                throw SubscriptionImportException("发送设备拒绝了传输或链接已失效")
            }
            all.copyOfRange(headerEnd + separator.size, all.size)
        }
    }
}

private fun java.io.InputStream.readBounded(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (output.size() + count > maxBytes) {
            throw SubscriptionImportException("传输内容超过限制")
        }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private fun ByteArray.indexOf(needle: ByteArray): Int {
    if (needle.isEmpty() || size < needle.size) return -1
    outer@ for (index in 0..size - needle.size) {
        for (offset in needle.indices) {
            if (this[index + offset] != needle[offset]) continue@outer
        }
        return index
    }
    return -1
}
