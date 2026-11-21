package com.example.domain

import java.net.URLDecoder
import java.text.Normalizer

enum class DomainCategory(val titleResKey: String) {
    COLOMBIA_COLJUEGOS("Colombia (Coljuegos)"),
    CASINOS("Casinos con apuestas"),
    INTERNACIONALES("Internacionales"),
    PRONOSTICOS_TIPSTERS("Pronósticos y tipsters"),
    CRIPTO_APUESTAS("Cripto-apuestas")
}

data class DomainGroup(
    val category: DomainCategory,
    val label: String,
    val domains: List<String>
)

object BlockedDomains {

    val GROUPS: List<DomainGroup> = listOf(
        DomainGroup(
            category = DomainCategory.COLOMBIA_COLJUEGOS,
            label = "Colombia (Coljuegos)",
            domains = listOf(
                "betplay.com.co",
                "wplay.co",
                "codere.com.co",
                "rushbet.co",
                "sportium.com.co",
                "luckia.com.co",
                "zbet.com.co",
                "ganabet.com.co",
                "rivalo.com",
                "bet365.com",
                "bettilt.com",
                "1xbet.com",
                "dafabet.com",
                "parimatch.com",
                "librabet.com"
            )
        ),
        DomainGroup(
            category = DomainCategory.CASINOS,
            label = "Casinos con apuestas",
            domains = listOf(
                "casino.betplay.com.co",
                "casinoganabet.com.co",
                "vegasplus.co",
                "juegging.com",
                "zitrocasino.com",
                "hollywoodcasinocolombia.com"
            )
        ),
        DomainGroup(
            category = DomainCategory.INTERNACIONALES,
            label = "Internacionales",
            domains = listOf(
                "betsson.com",
                "bwin.com",
                "888sport.com",
                "williamhill.com",
                "unibet.com",
                "pinnacle.com",
                "betway.com",
                "draftkings.com",
                "fanduel.com",
                "stake.com",
                "bc.game",
                "rollbit.com",
                "roobet.com",
                "cloudbet.com",
                "sportsbet.io",
                "sportsbetting.ag"
            )
        ),
        DomainGroup(
            category = DomainCategory.PRONOSTICOS_TIPSTERS,
            label = "Pronósticos y tipsters",
            domains = listOf(
                "betexplorer.com",
                "oddschecker.com",
                "oddsportal.com",
                "forebet.com",
                "bettingexpert.com",
                "windrawwin.com",
                "soccervista.com",
                "predictz.com",
                "betimate.com",
                "tips180.com",
                "zulubet.com",
                "pronosticos.com",
                "apuestasdeportivas.com",
                "comunidadbet.com"
            )
        ),
        DomainGroup(
            category = DomainCategory.CRIPTO_APUESTAS,
            label = "Cripto-apuestas",
            domains = listOf(
                "fortunejack.com",
                "bitcasino.io",
                "1xbit.com",
                "betfury.io",
                "wolf.bet"
            )
        )
    )

    val DOMAINS: List<String> = GROUPS.flatMap { it.domains }.distinct()

    fun normalizeHost(rawHost: String): String {
        return rawHost
            .trim()
            .lowercase()
            .substringBefore(':')
            .trimEnd('.')
    }

    fun isBlockedDomain(rawHost: String): Boolean {
        val host = normalizeHost(rawHost)
        if (host.isEmpty()) return false
        return DOMAINS.any { blocked ->
            host == blocked || host.endsWith(".$blocked")
        }
    }

    fun matchedRootDomain(rawHost: String): String? {
        val host = normalizeHost(rawHost)
        if (host.isEmpty()) return null
        return DOMAINS.firstOrNull { blocked ->
            host == blocked || host.endsWith(".$blocked")
        }
    }
}

object SearchKeywordBlocker {

    val BLOCKED_KEYWORDS: List<String> = listOf(
        "apuestas",
        "betplay",
        "wplay",
        "pronósticos",
        "cuotas",
        "momios",
        "tipster",
        "quiniela",
        "casino online"
    )

    private val NORMALIZED_KEYWORDS: List<String> = BLOCKED_KEYWORDS.map { normalizeText(it) }

    fun normalizeText(input: String): String {
        val decomposed = Normalizer.normalize(input.lowercase(), Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun findBlockedKeywordInGoogleSearch(rawTextOrUrl: String): String? {
        if (rawTextOrUrl.isBlank()) return null
        val lower = rawTextOrUrl.lowercase()

        // If it looks like a Google search URL, extract the query parameter `q=`
        if (lower.contains("google.") && (lower.contains("q=") || lower.contains("/search"))) {
            val queryPart = extractQueryParam(rawTextOrUrl) ?: rawTextOrUrl
            return matchKeyword(queryPart)
        }
        return null
    }

    fun matchKeyword(rawQuery: String): String? {
        if (rawQuery.isBlank()) return null
        val normalized = normalizeText(rawQuery)
        if (normalized.isEmpty()) return null

        for (index in NORMALIZED_KEYWORDS.indices) {
            val keyword = NORMALIZED_KEYWORDS[index]
            val pattern = if (keyword.contains(' ')) {
                Regex("(^|\\b)${Regex.escape(keyword)}(\\b|$)")
            } else {
                Regex("(^|\\b)${Regex.escape(keyword)}(\\b|$)")
            }
            if (pattern.containsMatchIn(normalized)) {
                return BLOCKED_KEYWORDS[index]
            }
        }
        return null
    }

    private fun extractQueryParam(url: String): String? {
        val queryIndex = url.indexOf('?')
        val fragmentIndex = url.indexOf('#')
        val paramSegment = when {
            queryIndex >= 0 -> url.substring(queryIndex + 1)
            fragmentIndex >= 0 -> url.substring(fragmentIndex + 1)
            else -> return null
        }
        val params = paramSegment.split('&', '#')
        for (param in params) {
            if (param.startsWith("q=", ignoreCase = true)) {
                val rawValue = param.substring(2)
                return try {
                    URLDecoder.decode(rawValue, Charsets.UTF_8.name())
                } catch (_: Exception) {
                    rawValue.replace('+', ' ')
                }
            }
        }
        return null
    }
}
