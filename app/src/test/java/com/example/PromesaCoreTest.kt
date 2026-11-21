package com.example

import com.example.data.PinHasher
import com.example.domain.BlockedDomains
import com.example.domain.SearchKeywordBlocker
import com.example.utils.WhatsAppHelper
import com.example.vpn.DnsPacketProcessor
import com.example.vpn.NextDnsResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromesaCoreTest {

    @Test
    fun `blocks all requested gambling, casino, tipster, and crypto domains with wildcard subdomains`() {
        val requiredDomains = listOf(
            // Colombia
            "betplay.com.co", "wplay.co", "codere.com.co", "rushbet.co",
            "sportium.com.co", "luckia.com.co", "zbet.com.co", "ganabet.com.co",
            "rivalo.com", "bet365.com", "bettilt.com", "1xbet.com",
            "dafabet.com", "parimatch.com", "librabet.com",
            // Casinos
            "casino.betplay.com.co", "casinoganabet.com.co", "vegasplus.co",
            "juegging.com", "zitrocasino.com", "hollywoodcasinocolombia.com",
            // Internacionales
            "betsson.com", "bwin.com", "888sport.com", "williamhill.com",
            "unibet.com", "pinnacle.com", "betway.com", "draftkings.com",
            "fanduel.com", "stake.com", "bc.game", "rollbit.com",
            "roobet.com", "cloudbet.com", "sportsbet.io", "sportsbetting.ag",
            // Pronósticos y tipsters
            "betexplorer.com", "oddschecker.com", "oddsportal.com", "forebet.com",
            "bettingexpert.com", "windrawwin.com", "soccervista.com", "predictz.com",
            "betimate.com", "tips180.com", "zulubet.com", "pronosticos.com",
            "apuestasdeportivas.com", "comunidadbet.com",
            // Cripto-apuestas
            "fortunejack.com", "bitcasino.io", "1xbit.com", "betfury.io", "wolf.bet"
        )

        requiredDomains.forEach { domain ->
            assertTrue("Expected root $domain to be blocked", BlockedDomains.isBlockedDomain(domain))
            assertTrue(
                "Expected subdomain m.$domain to be blocked",
                BlockedDomains.isBlockedDomain("m.$domain")
            )
            assertTrue(
                "Expected deep subdomain api.live.$domain to be blocked",
                BlockedDomains.isBlockedDomain("api.live.$domain")
            )
        }

        val allowed = listOf(
            "google.com",
            "notbetplay.com.co",
            "betplay.com.co.evil.org",
            "wikipedia.org",
            "wplay.com"
        )
        allowed.forEach { host ->
            assertFalse("Expected $host to be allowed", BlockedDomains.isBlockedDomain(host))
        }
    }

    @Test
    fun `search keyword blocker catches all 9 betting keywords in Google queries`() {
        val blockedQueries = listOf(
            "https://www.google.com/search?q=mejores+apuestas+hoy",
            "https://www.google.com.co/search?q=betplay+en+vivo",
            "https://www.google.com/search?q=wplay+bono",
            "https://www.google.com/search?q=pronosticos+liga+betplay",
            "https://www.google.com/search?q=pron%C3%B3sticos+futbol",
            "https://www.google.com/search?q=cuotas+colombia+brasil",
            "https://www.google.com/search?q=momios+nba",
            "https://www.google.com/search?q=mejor+tipster+telegram",
            "https://www.google.com/search?q=quiniela+semana",
            "https://www.google.com/search?q=casino+online+colombia"
        )
        blockedQueries.forEach { url ->
            assertNotNull(
                "Expected search URL to be blocked: $url",
                SearchKeywordBlocker.findBlockedKeywordInGoogleSearch(url)
            )
        }

        assertNull(
            SearchKeywordBlocker.findBlockedKeywordInGoogleSearch(
                "https://www.google.com/search?q=receta+de+arepas+colombianas"
            )
        )
    }

    @Test
    fun `pin hasher enforces min 4 digits and verifies securely`() {
        assertFalse(PinHasher.isValidPinFormat("123"))
        assertFalse(PinHasher.isValidPinFormat("12a4"))
        assertTrue(PinHasher.isValidPinFormat("1234"))
        assertTrue(PinHasher.isValidPinFormat("123456"))

        val hashed = PinHasher.createHashedPin("2580")
        assertTrue(PinHasher.verifyPin("2580", hashed.hashBase64, hashed.saltBase64))
        assertFalse(PinHasher.verifyPin("0000", hashed.hashBase64, hashed.saltBase64))
    }

    @Test
    fun `dns packet processor parses ipv4 query and builds nxdomain reply`() {
        val dnsQuery = buildDnsQueryPayload("m.betplay.com.co")
        val ipv4UdpPacket = wrapIpv4UdpPacket(
            srcIp = byteArrayOf(10, 111, 222.toByte(), 1),
            dstIp = byteArrayOf(10, 111, 222.toByte(), 2),
            srcPort = 54321,
            dstPort = 53,
            payload = dnsQuery
        )

        val parsed = DnsPacketProcessor.parseDnsPacket(ipv4UdpPacket, ipv4UdpPacket.size)
        assertNotNull(parsed)
        assertEquals("m.betplay.com.co", parsed!!.qname)
        assertEquals(1, parsed.qtype)

        val nxDns = DnsPacketProcessor.buildNxDomainDnsResponse(parsed.dnsPayload)
        assertTrue((nxDns[2].toInt() and 0x80) != 0)
        assertEquals(3, nxDns[3].toInt() and 0x0F)

        val replyPacket = DnsPacketProcessor.buildIpUdpReplyPacket(parsed, nxDns)
        assertEquals(20 + 8 + nxDns.size, replyPacket.size)
        assertEquals(10, replyPacket[16].toInt() and 0xFF)
        assertEquals(1, replyPacket[19].toInt() and 0xFF)
    }

    @Test
    fun `nextdns resolver permanently targets 53cf8e and frames DoT queries`() {
        assertEquals("53cf8e.dns.nextdns.io", NextDnsResolver.NEXTDNS_DOT_HOSTNAME)
        assertEquals("/53cf8e", NextDnsResolver.NEXTDNS_DOH_PATH)
        assertEquals(853, NextDnsResolver.DOT_PORT)
        assertTrue(NextDnsResolver.NEXTDNS_ANYCAST_IPS.contains("45.90.28.0"))

        val payload = buildDnsQueryPayload("example.com")
        val framed = NextDnsResolver.frameDotQuery(payload)
        assertEquals(payload.size + 2, framed.size)
        val decodedLen = ((framed[0].toInt() and 0xFF) shl 8) or (framed[1].toInt() and 0xFF)
        assertEquals(payload.size, decodedLen)
    }

    @Test
    fun `whatsapp helper sanitizes phone numbers cleanly`() {
        assertEquals("573001234567", WhatsAppHelper.sanitizePhoneDigits("+57 300 123-4567"))
    }

    private fun buildDnsQueryPayload(domain: String): ByteArray {
        val labels = domain.split('.')
        val qnameBytes = mutableListOf<Byte>()
        for (label in labels) {
            qnameBytes.add(label.length.toByte())
            label.toByteArray(Charsets.US_ASCII).forEach { qnameBytes.add(it) }
        }
        qnameBytes.add(0)

        val out = ByteArray(12 + qnameBytes.size + 4)
        out[0] = 0x12
        out[1] = 0x34
        out[2] = 0x01
        out[3] = 0x00
        out[5] = 0x01
        qnameBytes.forEachIndexed { idx, b -> out[12 + idx] = b }
        val qtypeOffset = 12 + qnameBytes.size
        out[qtypeOffset + 1] = 0x01
        out[qtypeOffset + 3] = 0x01
        return out
    }

    private fun wrapIpv4UdpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val udpLen = 8 + payload.size
        val totalLen = 20 + udpLen
        val out = ByteArray(totalLen)
        out[0] = 0x45
        out[2] = ((totalLen ushr 8) and 0xFF).toByte()
        out[3] = (totalLen and 0xFF).toByte()
        out[8] = 64
        out[9] = 17
        System.arraycopy(srcIp, 0, out, 12, 4)
        System.arraycopy(dstIp, 0, out, 16, 4)
        out[20] = ((srcPort ushr 8) and 0xFF).toByte()
        out[21] = (srcPort and 0xFF).toByte()
        out[22] = ((dstPort ushr 8) and 0xFF).toByte()
        out[23] = (dstPort and 0xFF).toByte()
        out[24] = ((udpLen ushr 8) and 0xFF).toByte()
        out[25] = (udpLen and 0xFF).toByte()
        System.arraycopy(payload, 0, out, 28, payload.size)
        return out
    }
}
