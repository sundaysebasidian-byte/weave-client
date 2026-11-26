package io.weave.client.transfer

import io.weave.client.subscription.SubscriptionImportException
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
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
    private var accepted: Socket? = null
    private var job: Job? = null

    fun start(
        packet: ByteArray,
        expiryMs: Long = 5 * 60_000L,
        ensureActive: () -> Unit = {},
    ): LanTransferLink {
        val host = localPrivateIpv4()
            ?: throw SubscriptionImportException("未找到可用的局域网 IPv4 地址")
        val token = LanTransferCodec.randomToken()
        val key = LanTransferCodec.randomKey()
        val sealed = LanTransferCodec.seal(packet, key)
        val port = synchronized(this) {
            ensureActive()
            stop()
            startServing(host, token, sealed, expiryMs)
        }
        return LanTransferLink(host, port, token, key)
    }

    private fun startServing(host: String, token: String, sealed: ByteArray, expiryMs: Long): Int {
        // Bind only to the selected private interface. A wildcard listener would expose the
        // one-time transfer endpoint to other local interfaces (including tethering/USB links).
        val server = ServerSocket()
        try {
            server.reuseAddress = false
            server.bind(InetSocketAddress(host, 0))
        } catch (error: IOException) {
            runCatching { server.close() }
            throw error
        }
        socket = server
        job = scope.launch {
            try {
                launch {
                    delay(expiryMs)
                    stop(server)
                }
                var consumed = false
                while (!server.isClosed && !consumed) {
                    server.accept().use { client ->
                        val owned = synchronized(this@OneTimeLanTransferServer) {
                            if (socket !== server) false else {
                                accepted = client
                                true
                            }
                        }
                        if (!owned) return@launch
                        try {
                            val requestLine = readHeader(client).lineSequence().firstOrNull()
                            consumed = requestLine == "GET /v1/$token HTTP/1.0" ||
                                requestLine == "GET /v1/$token HTTP/1.1"
                            sendResponse(client, sealed.takeIf { consumed })
                        } catch (_: IOException) {
                            // A peer leaving before its request completes must not tear down a share.
                        } finally {
                            synchronized(this@OneTimeLanTransferServer) {
                                if (accepted === client) accepted = null
                            }
                        }
                    }
                }
            } catch (_: IOException) {
                // Listener closed on expiration or by the sender.
            } finally {
                stop(server)
            }
        }
        return server.localPort
    }

    private fun sendResponse(client: Socket, packet: ByteArray?) {
        val header = if (packet != null) {
            "HTTP/1.1 200 OK\r\nContent-Type: application/vnd.weave.transfer\r\n" +
                "Cache-Control: no-store\r\nContent-Length: ${packet.size}\r\nConnection: close\r\n\r\n"
        } else {
            "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        }
        client.getOutputStream().apply {
            write(header.toByteArray(Charsets.US_ASCII))
            if (packet != null) write(packet)
            flush()
        }
    }

    @Synchronized
    private fun stop(ownedSocket: ServerSocket) {
        if (socket === ownedSocket) stop()
        else runCatching { ownedSocket.close() }
    }

    @Synchronized
    fun stop() {
        runCatching { accepted?.close() }
        accepted = null
        runCatching { socket?.close() }
        socket = null
        job?.cancel()
        job = null
    }

    private fun readHeader(socket: Socket): String {
        val output = ByteArrayOutputStream()
        val input = socket.transferInput(timeoutSeconds = 5)
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
        return if (matched == 4) output.toString(Charsets.US_ASCII.name()) else ""
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
        Socket().use { socket ->
            socket.connect(InetSocketAddress(link.host, link.port), 10_000)
            socket.getOutputStream().apply {
                write(
                    (
                        "GET /v1/${link.token} HTTP/1.1\r\nHost: ${link.host}\r\n" +
                            "Connection: close\r\n\r\n"
                        ).toByteArray(),
                )
                flush()
            }
            readLanTransferResponse(socket.transferInput())
        }
    }
}

private fun Socket.transferInput(timeoutSeconds: Long = 45): InputStream = object : FilterInputStream(getInputStream()) {
    private val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)

    private fun checkBudget() {
        val remaining = deadline - System.nanoTime()
        if (remaining <= 0) throw SocketTimeoutException()
        val timeout = TimeUnit.NANOSECONDS.toMillis(remaining).coerceIn(1, 10_000).toInt()
        if (soTimeout != timeout) soTimeout = timeout
    }

    override fun read(): Int {
        checkBudget()
        return super.read()
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        checkBudget()
        return super.read(bytes, offset, length)
    }
}.buffered(16 * 1024)

internal fun readLanTransferResponse(input: InputStream): ByteArray {
    val headerBytes = ByteArray(4_096)
    var end = 0
    for (index in headerBytes.indices) {
        val next = input.read()
        if (next < 0) break
        headerBytes[index] = next.toByte()
        if (index >= 3 && next == '\n'.code && headerBytes[index - 1] == '\r'.code.toByte() &&
            headerBytes[index - 2] == '\n'.code.toByte() && headerBytes[index - 3] == '\r'.code.toByte()
        ) {
            end = index + 1
            break
        }
    }
    if (end == 0) throw SubscriptionImportException("发送设备返回无效响应")
    val header = String(headerBytes, 0, end - 4, Charsets.US_ASCII).lineSequence().iterator()
    if (!header.next().startsWith("HTTP/1.1 200 ")) {
        throw SubscriptionImportException("发送设备拒绝了传输或链接已失效")
    }
    var type: String? = null
    var length: String? = null
    for (line in header) {
        val separator = line.indexOf(':')
        if (separator < 0) continue
        val name = line.substring(0, separator).trim()
        val value = line.substring(separator + 1).trim()
        when {
            name.equals("content-type", ignoreCase = true) -> {
                if (type != null) throw SubscriptionImportException("发送设备返回无效响应")
                type = value
            }
            name.equals("content-length", ignoreCase = true) -> {
                if (length != null) throw SubscriptionImportException("发送设备返回的传输长度无效")
                length = value
            }
        }
    }
    if (!"application/vnd.weave.transfer".equals(type, ignoreCase = true)) {
        throw SubscriptionImportException("发送设备返回了不受信任的传输类型")
    }
    val declared = length?.toIntOrNull() ?: throw SubscriptionImportException("发送设备缺少传输长度")
    if (declared !in 1..LanTransferCodec.MAX_CIPHERTEXT_BYTES) {
        throw SubscriptionImportException("发送设备返回的传输长度无效")
    }
    val packet = ByteArray(declared)
    var offset = 0
    while (offset < packet.size) {
        val count = input.read(packet, offset, packet.size - offset)
        if (count <= 0) throw SubscriptionImportException("发送设备返回的传输长度无效")
        offset += count
    }
    if (input.read() >= 0) throw SubscriptionImportException("发送设备返回的传输长度无效")
    return packet
}
