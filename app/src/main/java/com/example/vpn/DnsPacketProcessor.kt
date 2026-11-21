package com.example.vpn

data class ParsedDnsPacket(
    val ipVersion: Int,
    val srcIp: ByteArray,
    val dstIp: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    val dnsPayload: ByteArray,
    val qname: String,
    val qtype: Int
)

object DnsPacketProcessor {

    private const val PROTOCOL_UDP = 17
    private const val DNS_PORT = 53
    private const val IPV4_HEADER_MIN_LEN = 20
    private const val IPV6_HEADER_LEN = 40
    private const val UDP_HEADER_LEN = 8
    private const val DNS_HEADER_LEN = 12

    fun parseDnsPacket(packet: ByteArray, length: Int): ParsedDnsPacket? {
        if (length < IPV4_HEADER_MIN_LEN) return null
        val version = (packet[0].toInt() ushr 4) and 0x0F
        return when (version) {
            4 -> parseIpv4Dns(packet, length)
            6 -> parseIpv6Dns(packet, length)
            else -> null
        }
    }

    private fun parseIpv4Dns(packet: ByteArray, length: Int): ParsedDnsPacket? {
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl < IPV4_HEADER_MIN_LEN || length < ihl + UDP_HEADER_LEN + DNS_HEADER_LEN) {
            return null
        }
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != PROTOCOL_UDP) return null

        val totalLen = readUShort(packet, 2).coerceAtMost(length)
        if (totalLen < ihl + UDP_HEADER_LEN + DNS_HEADER_LEN) return null

        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)

        return parseUdpDns(
            ipVersion = 4,
            packet = packet,
            ipHeaderLen = ihl,
            packetLimit = totalLen,
            srcIp = srcIp,
            dstIp = dstIp
        )
    }

    private fun parseIpv6Dns(packet: ByteArray, length: Int): ParsedDnsPacket? {
        if (length < IPV6_HEADER_LEN + UDP_HEADER_LEN + DNS_HEADER_LEN) return null
        val nextHeader = packet[6].toInt() and 0xFF
        if (nextHeader != PROTOCOL_UDP) return null

        val payloadLen = readUShort(packet, 4)
        val totalLen = (IPV6_HEADER_LEN + payloadLen).coerceAtMost(length)
        if (totalLen < IPV6_HEADER_LEN + UDP_HEADER_LEN + DNS_HEADER_LEN) return null

        val srcIp = packet.copyOfRange(8, 24)
        val dstIp = packet.copyOfRange(24, 40)

        return parseUdpDns(
            ipVersion = 6,
            packet = packet,
            ipHeaderLen = IPV6_HEADER_LEN,
            packetLimit = totalLen,
            srcIp = srcIp,
            dstIp = dstIp
        )
    }

    private fun parseUdpDns(
        ipVersion: Int,
        packet: ByteArray,
        ipHeaderLen: Int,
        packetLimit: Int,
        srcIp: ByteArray,
        dstIp: ByteArray
    ): ParsedDnsPacket? {
        val srcPort = readUShort(packet, ipHeaderLen)
        val dstPort = readUShort(packet, ipHeaderLen + 2)
        if (dstPort != DNS_PORT) return null

        val udpLength = readUShort(packet, ipHeaderLen + 4)
        if (udpLength < UDP_HEADER_LEN + DNS_HEADER_LEN) return null

        val dnsStart = ipHeaderLen + UDP_HEADER_LEN
        val dnsEnd = (ipHeaderLen + udpLength).coerceAtMost(packetLimit)
        if (dnsEnd - dnsStart < DNS_HEADER_LEN) return null

        val dnsPayload = packet.copyOfRange(dnsStart, dnsEnd)
        // Check QR bit == 0 (standard query) and QDCOUNT >= 1
        val flags = readUShort(dnsPayload, 2)
        if ((flags and 0x8000) != 0) return null
        val qdCount = readUShort(dnsPayload, 4)
        if (qdCount < 1) return null

        val parsedQuestion = parseQuestion(dnsPayload) ?: return null

        return ParsedDnsPacket(
            ipVersion = ipVersion,
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = srcPort,
            dstPort = dstPort,
            dnsPayload = dnsPayload,
            qname = parsedQuestion.first,
            qtype = parsedQuestion.second
        )
    }

    private fun parseQuestion(dnsPayload: ByteArray): Pair<String, Int>? {
        val labels = mutableListOf<String>()
        var offset = DNS_HEADER_LEN
        var steps = 0

        while (offset < dnsPayload.size && steps < 128) {
            steps++
            val labelLen = dnsPayload[offset].toInt() and 0xFF
            if (labelLen == 0) {
                offset += 1
                break
            }
            if ((labelLen and 0xC0) != 0 || labelLen > 63) {
                return null
            }
            offset += 1
            if (offset + labelLen > dnsPayload.size) return null
            val label = String(dnsPayload, offset, labelLen, Charsets.US_ASCII).lowercase()
            labels.add(label)
            offset += labelLen
        }

        if (labels.isEmpty() || offset + 4 > dnsPayload.size) return null
        val qtype = readUShort(dnsPayload, offset)
        return labels.joinToString(".") to qtype
    }

    fun buildNxDomainDnsResponse(queryPayload: ByteArray): ByteArray {
        if (queryPayload.size < DNS_HEADER_LEN) return queryPayload

        val questionEnd = findQuestionEndOffset(queryPayload) ?: DNS_HEADER_LEN
        val response = queryPayload.copyOf(questionEnd)

        val rdBit = queryPayload[2].toInt() and 0x01
        // QR = 1, OPCODE = 0, AA = 0, TC = 0, RD = preserved
        response[2] = (0x80 or rdBit).toByte()
        // RA = 1, Z = 0, RCODE = 3 (NXDOMAIN)
        response[3] = (0x80 or 0x03).toByte()

        // QDCOUNT = 1 if question was parsed, else 0
        if (questionEnd > DNS_HEADER_LEN) {
            writeUShort(response, 4, 1)
        } else {
            writeUShort(response, 4, 0)
        }
        // ANCOUNT, NSCOUNT, ARCOUNT = 0
        writeUShort(response, 6, 0)
        writeUShort(response, 8, 0)
        writeUShort(response, 10, 0)

        return response
    }

    private fun findQuestionEndOffset(dnsPayload: ByteArray): Int? {
        var offset = DNS_HEADER_LEN
        var steps = 0
        while (offset < dnsPayload.size && steps < 128) {
            steps++
            val labelLen = dnsPayload[offset].toInt() and 0xFF
            if (labelLen == 0) {
                offset += 1
                break
            }
            if ((labelLen and 0xC0) != 0 || labelLen > 63) return null
            offset += 1 + labelLen
        }
        return if (offset + 4 <= dnsPayload.size) offset + 4 else null
    }

    fun buildIpUdpReplyPacket(request: ParsedDnsPacket, dnsResponse: ByteArray): ByteArray {
        return if (request.ipVersion == 6) {
            buildIpv6UdpReply(request, dnsResponse)
        } else {
            buildIpv4UdpReply(request, dnsResponse)
        }
    }

    private fun buildIpv4UdpReply(request: ParsedDnsPacket, dnsResponse: ByteArray): ByteArray {
        val udpLen = UDP_HEADER_LEN + dnsResponse.size
        val totalLen = IPV4_HEADER_MIN_LEN + udpLen
        val out = ByteArray(totalLen)

        // IPv4 Header
        out[0] = 0x45
        out[1] = 0x00
        writeUShort(out, 2, totalLen)
        // ID from DNS transaction ID
        if (dnsResponse.size >= 2) {
            out[4] = dnsResponse[0]
            out[5] = dnsResponse[1]
        }
        out[6] = 0x40 // Don't fragment
        out[7] = 0x00
        out[8] = 64 // TTL
        out[9] = PROTOCOL_UDP.toByte()
        // Swap src <-> dst IP
        System.arraycopy(request.dstIp, 0, out, 12, 4)
        System.arraycopy(request.srcIp, 0, out, 16, 4)
        // IPv4 header checksum
        val ipChecksum = internetChecksum(out, 0, IPV4_HEADER_MIN_LEN)
        writeUShort(out, 10, ipChecksum)

        // UDP Header
        writeUShort(out, IPV4_HEADER_MIN_LEN, request.dstPort)
        writeUShort(out, IPV4_HEADER_MIN_LEN + 2, request.srcPort)
        writeUShort(out, IPV4_HEADER_MIN_LEN + 4, udpLen)
        writeUShort(out, IPV4_HEADER_MIN_LEN + 6, 0)

        // DNS Payload
        System.arraycopy(dnsResponse, 0, out, IPV4_HEADER_MIN_LEN + UDP_HEADER_LEN, dnsResponse.size)

        // UDP checksum over IPv4 pseudo-header + UDP segment
        val udpChecksum = computeUdpChecksumIpv4(
            srcIp = request.dstIp,
            dstIp = request.srcIp,
            udpSegment = out,
            udpOffset = IPV4_HEADER_MIN_LEN,
            udpLength = udpLen
        )
        writeUShort(out, IPV4_HEADER_MIN_LEN + 6, udpChecksum)

        return out
    }

    private fun buildIpv6UdpReply(request: ParsedDnsPacket, dnsResponse: ByteArray): ByteArray {
        val udpLen = UDP_HEADER_LEN + dnsResponse.size
        val totalLen = IPV6_HEADER_LEN + udpLen
        val out = ByteArray(totalLen)

        // IPv6 Header
        out[0] = 0x60
        out[1] = 0x00
        out[2] = 0x00
        out[3] = 0x00
        writeUShort(out, 4, udpLen)
        out[6] = PROTOCOL_UDP.toByte()
        out[7] = 64 // Hop limit
        System.arraycopy(request.dstIp, 0, out, 8, 16)
        System.arraycopy(request.srcIp, 0, out, 24, 16)

        // UDP Header
        writeUShort(out, IPV6_HEADER_LEN, request.dstPort)
        writeUShort(out, IPV6_HEADER_LEN + 2, request.srcPort)
        writeUShort(out, IPV6_HEADER_LEN + 4, udpLen)
        writeUShort(out, IPV6_HEADER_LEN + 6, 0)

        // DNS Payload
        System.arraycopy(dnsResponse, 0, out, IPV6_HEADER_LEN + UDP_HEADER_LEN, dnsResponse.size)

        val udpChecksum = computeUdpChecksumIpv6(
            srcIp = request.dstIp,
            dstIp = request.srcIp,
            udpSegment = out,
            udpOffset = IPV6_HEADER_LEN,
            udpLength = udpLen
        )
        writeUShort(out, IPV6_HEADER_LEN + 6, udpChecksum)

        return out
    }

    private fun computeUdpChecksumIpv4(
        srcIp: ByteArray,
        dstIp: ByteArray,
        udpSegment: ByteArray,
        udpOffset: Int,
        udpLength: Int
    ): Int {
        var sum = 0L
        sum += sumWords(srcIp, 0, 4)
        sum += sumWords(dstIp, 0, 4)
        sum += PROTOCOL_UDP.toLong()
        sum += udpLength.toLong()
        sum += sumWords(udpSegment, udpOffset, udpLength)
        val finalized = foldChecksum(sum)
        return if (finalized == 0) 0xFFFF else finalized
    }

    private fun computeUdpChecksumIpv6(
        srcIp: ByteArray,
        dstIp: ByteArray,
        udpSegment: ByteArray,
        udpOffset: Int,
        udpLength: Int
    ): Int {
        var sum = 0L
        sum += sumWords(srcIp, 0, 16)
        sum += sumWords(dstIp, 0, 16)
        sum += ((udpLength ushr 16) and 0xFFFF).toLong()
        sum += (udpLength and 0xFFFF).toLong()
        sum += PROTOCOL_UDP.toLong()
        sum += sumWords(udpSegment, udpOffset, udpLength)
        val finalized = foldChecksum(sum)
        return if (finalized == 0) 0xFFFF else finalized
    }

    private fun internetChecksum(buffer: ByteArray, offset: Int, length: Int): Int {
        return foldChecksum(sumWords(buffer, offset, length))
    }

    private fun sumWords(buffer: ByteArray, offset: Int, length: Int): Long {
        var sum = 0L
        var i = 0
        while (i + 1 < length) {
            val high = buffer[offset + i].toInt() and 0xFF
            val low = buffer[offset + i + 1].toInt() and 0xFF
            sum += ((high shl 8) or low).toLong()
            i += 2
        }
        if (i < length) {
            val high = buffer[offset + i].toInt() and 0xFF
            sum += (high shl 8).toLong()
        }
        return sum
    }

    private fun foldChecksum(initialSum: Long): Int {
        var sum = initialSum
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFFL) + (sum ushr 16)
        }
        return (sum.inv() and 0xFFFFL).toInt()
    }

    private fun readUShort(buffer: ByteArray, offset: Int): Int {
        return ((buffer[offset].toInt() and 0xFF) shl 8) or
            (buffer[offset + 1].toInt() and 0xFF)
    }

    private fun writeUShort(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = ((value ushr 8) and 0xFF).toByte()
        buffer[offset + 1] = (value and 0xFF).toByte()
    }
}
