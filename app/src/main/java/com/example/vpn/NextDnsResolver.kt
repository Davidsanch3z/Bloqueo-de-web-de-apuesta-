package com.example.vpn

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedDeque
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Resolves all non-locally-blocked DNS queries exclusively through NextDNS profile 53cf8e
 * (53cf8e.dns.nextdns.io) using DNS-over-TLS (port 853) with DNS-over-HTTPS fallback (port 443).
 */
class NextDnsResolver(
    private val protectSocket: (Socket) -> Boolean
) {

    private val dotPool = ConcurrentLinkedDeque<SSLSocket>()

    fun resolve(dnsQuery: ByteArray): ByteArray? {
        if (dnsQuery.size < 12) return null

        // Primary: DNS-over-TLS directly to 53cf8e.dns.nextdns.io:853
        resolveViaDot(dnsQuery)?.let { return it }

        // Fallback: DNS-over-HTTPS to https://dns.nextdns.io/53cf8e on port 443
        return resolveViaDoh(dnsQuery)
    }

    private fun resolveViaDot(dnsQuery: ByteArray): ByteArray? {
        // First try a pooled TLS connection
        while (true) {
            val pooled = dotPool.pollFirst() ?: break
            if (pooled.isClosed || !pooled.isConnected) {
                closeQuietly(pooled)
                continue
            }
            val result = exchangeDot(pooled, dnsQuery)
            if (result != null) {
                returnDotSocket(pooled)
                return result
            } else {
                closeQuietly(pooled)
            }
        }

        // Otherwise open a fresh protected DoT connection to NextDNS Anycast endpoints
        for (endpointIp in NEXTDNS_ANYCAST_IPS) {
            var socket: SSLSocket? = null
            try {
                socket = openProtectedTlsSocket(
                    ip = endpointIp,
                    port = DOT_PORT,
                    sniHost = NEXTDNS_DOT_HOSTNAME
                ) ?: continue

                val result = exchangeDot(socket, dnsQuery)
                if (result != null) {
                    returnDotSocket(socket)
                    return result
                } else {
                    closeQuietly(socket)
                }
            } catch (_: Exception) {
                closeQuietly(socket)
            }
        }
        return null
    }

    private fun exchangeDot(socket: SSLSocket, dnsQuery: ByteArray): ByteArray? {
        return try {
            val out = DataOutputStream(socket.outputStream)
            out.writeShort(dnsQuery.size)
            out.write(dnsQuery)
            out.flush()

            val input = DataInputStream(socket.inputStream)
            val responseLen = input.readUnsignedShort()
            if (responseLen < 12 || responseLen > MAX_DNS_TCP_LEN) {
                return null
            }
            val response = ByteArray(responseLen)
            input.readFully(response)
            response
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveViaDoh(dnsQuery: ByteArray): ByteArray? {
        for (endpointIp in NEXTDNS_ANYCAST_IPS) {
            var socket: SSLSocket? = null
            try {
                socket = openProtectedTlsSocket(
                    ip = endpointIp,
                    port = DOH_PORT,
                    sniHost = NEXTDNS_DOH_HOST
                ) ?: continue

                val requestHeaders = buildString {
                    append("POST ").append(NEXTDNS_DOH_PATH).append(" HTTP/1.1\r\n")
                    append("Host: ").append(NEXTDNS_DOH_HOST).append("\r\n")
                    append("Content-Type: application/dns-message\r\n")
                    append("Accept: application/dns-message\r\n")
                    append("Content-Length: ").append(dnsQuery.size).append("\r\n")
                    append("Connection: close\r\n\r\n")
                }.toByteArray(Charsets.US_ASCII)

                val out = socket.outputStream
                out.write(requestHeaders)
                out.write(dnsQuery)
                out.flush()

                val parsedBody = readHttpDnsResponse(BufferedInputStream(socket.inputStream))
                if (parsedBody != null && parsedBody.size >= 12) {
                    return parsedBody
                }
            } catch (_: Exception) {
                // Try next NextDNS Anycast IP
            } finally {
                closeQuietly(socket)
            }
        }
        return null
    }

    private fun openProtectedTlsSocket(
        ip: String,
        port: Int,
        sniHost: String
    ): SSLSocket? {
        val rawSocket = Socket()
        return try {
            if (!protectSocket(rawSocket)) {
                rawSocket.close()
                return null
            }
            rawSocket.tcpNoDelay = true
            rawSocket.soTimeout = SOCKET_TIMEOUT_MS
            rawSocket.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)

            val sslSocket = sslSocketFactory.createSocket(rawSocket, sniHost, port, true) as SSLSocket
            val params = SSLParameters().apply {
                endpointIdentificationAlgorithm = "HTTPS"
            }
            sslSocket.sslParameters = params
            sslSocket.soTimeout = SOCKET_TIMEOUT_MS
            sslSocket.startHandshake()
            sslSocket
        } catch (e: Exception) {
            closeQuietly(rawSocket)
            null
        }
    }

    private fun readHttpDnsResponse(input: BufferedInputStream): ByteArray? {
        val headerBytes = ByteArrayOutputStream()
        var state = 0
        while (true) {
            val b = input.read()
            if (b == -1) return null
            headerBytes.write(b)
            state = when {
                state == 0 && b == '\r'.code -> 1
                state == 1 && b == '\n'.code -> 2
                state == 2 && b == '\r'.code -> 3
                state == 3 && b == '\n'.code -> 4
                b == '\r'.code -> 1
                else -> 0
            }
            if (state == 4) break
            if (headerBytes.size() > 8192) return null
        }

        val headerText = headerBytes.toString(Charsets.US_ASCII.name())
        val lines = headerText.split("\r\n")
        val statusLine = lines.firstOrNull() ?: return null
        if (!statusLine.contains(" 200")) return null

        var contentLength: Int? = null
        var isChunked = false
        for (line in lines.drop(1)) {
            val lower = line.lowercase()
            if (lower.startsWith("content-length:")) {
                contentLength = line.substringAfter(':').trim().toIntOrNull()
            } else if (lower.startsWith("transfer-encoding:") && lower.contains("chunked")) {
                isChunked = true
            }
        }

        return when {
            contentLength != null && contentLength in 12..MAX_DNS_TCP_LEN -> {
                val body = ByteArray(contentLength)
                DataInputStream(input).readFully(body)
                body
            }
            isChunked -> readChunkedBody(input)
            else -> {
                val rest = input.readBytes()
                if (rest.size in 12..MAX_DNS_TCP_LEN) rest else null
            }
        }
    }

    private fun readChunkedBody(input: BufferedInputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        while (true) {
            val lineBuf = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b == -1) return null
                if (b == '\n'.code) break
                if (b != '\r'.code) lineBuf.write(b)
                if (lineBuf.size() > 64) return null
            }
            val sizeHex = lineBuf.toString(Charsets.US_ASCII.name()).substringBefore(';').trim()
            val chunkSize = sizeHex.toIntOrNull(16) ?: return null
            if (chunkSize == 0) break
            if (out.size() + chunkSize > MAX_DNS_TCP_LEN) return null

            val chunk = ByteArray(chunkSize)
            DataInputStream(input).readFully(chunk)
            out.write(chunk)

            // Consume trailing CRLF after chunk
            input.read()
            input.read()
        }
        return out.toByteArray()
    }

    private fun returnDotSocket(socket: SSLSocket) {
        if (dotPool.size >= MAX_POOL_SIZE || socket.isClosed) {
            closeQuietly(socket)
        } else {
            dotPool.offerFirst(socket)
        }
    }

    fun closeAll() {
        while (true) {
            val s = dotPool.pollFirst() ?: break
            closeQuietly(s)
        }
    }

    private fun closeQuietly(socket: Socket?) {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
    }

    companion object {
        const val NEXTDNS_DOT_HOSTNAME = "53cf8e.dns.nextdns.io"
        const val NEXTDNS_DOH_HOST = "dns.nextdns.io"
        const val NEXTDNS_DOH_PATH = "/53cf8e"
        const val DOT_PORT = 853
        const val DOH_PORT = 443
        private const val CONNECT_TIMEOUT_MS = 3000
        private const val SOCKET_TIMEOUT_MS = 3500
        private const val MAX_DNS_TCP_LEN = 16384
        private const val MAX_POOL_SIZE = 4

        val NEXTDNS_ANYCAST_IPS = listOf("45.90.28.0", "45.90.30.0")

        private val sslSocketFactory: SSLSocketFactory by lazy {
            SSLSocketFactory.getDefault() as SSLSocketFactory
        }

        fun frameDotQuery(dnsQuery: ByteArray): ByteArray {
            val framed = ByteArray(2 + dnsQuery.size)
            framed[0] = ((dnsQuery.size ushr 8) and 0xFF).toByte()
            framed[1] = (dnsQuery.size and 0xFF).toByte()
            System.arraycopy(dnsQuery, 0, framed, 2, dnsQuery.size)
            return framed
        }
    }
}
