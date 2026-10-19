package com.wyrix.browser.core

import java.net.IDN

/** Host split for display: the registrable-looking domain is emphasised, the rest is dimmed. */
data class HostParts(
    val host: String,
    val subdomain: String,
    val domain: String,
    val isPunycode: Boolean,
    val unicodeHint: String?,
)

/** Pure string-based URL helpers (no android.net.Uri so they are JVM-unit-testable). */
object UrlTools {
    const val MAX_URL_LENGTH = 8192

    private val SCHEME_PREFIX = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):")
    private val HOST_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://(?:[^/?#@]*@)?(\\[[^\\]]+]|[^/?#:]*)")
    private val CREDENTIALS = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://[^/?#]*@")
    private val URL_PARTS = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*)://([^/?#]*)(.*)$", RegexOption.DOT_MATCHES_ALL)
    private val SECOND_LEVEL = setOf("co", "com", "org", "net", "gov", "edu", "ac", "or", "ne", "go")

    fun scheme(url: String): String? =
        SCHEME_PREFIX.find(url)?.groupValues?.get(1)?.lowercase()

    fun hostOf(url: String): String? =
        HOST_REGEX.find(url)?.groupValues?.get(1)?.lowercase()?.takeIf { it.isNotEmpty() }

    fun hasCredentials(url: String): Boolean = CREDENTIALS.containsMatchIn(url)

    fun isWebUrl(url: String): Boolean =
        url.length <= MAX_URL_LENGTH && scheme(url) in setOf("http", "https") && hostOf(url) != null

    fun hostParts(url: String): HostParts? {
        val host = hostOf(url) ?: return null
        val labels = host.split('.')
        val isIp = host.startsWith("[") || labels.all { l -> l.isNotEmpty() && l.all { it.isDigit() } }
        val punycode = labels.any { it.startsWith("xn--") }
        val hint = if (punycode) runCatching { IDN.toUnicode(host) }.getOrNull()?.takeIf { it != host } else null
        if (isIp || labels.size <= 2) return HostParts(host, "", host, punycode, hint)
        val n = labels.size
        val take = if (labels[n - 2] in SECOND_LEVEL && labels[n - 1].length == 2 && n >= 3) 3 else 2
        return HostParts(
            host = host,
            subdomain = labels.dropLast(take).joinToString("."),
            domain = labels.takeLast(take).joinToString("."),
            isPunycode = punycode,
            unicodeHint = hint,
        )
    }

    /** Canonical form for deduplication: lowercase scheme/host, default ports and fragments removed. */
    fun normalize(url: String): String {
        val noFragment = url.trim().substringBefore('#')
        val m = URL_PARTS.matchEntire(noFragment) ?: return noFragment
        val scheme = m.groupValues[1].lowercase()
        var authority = m.groupValues[2].lowercase()
        if (scheme == "http" && authority.endsWith(":80")) authority = authority.removeSuffix(":80")
        if (scheme == "https" && authority.endsWith(":443")) authority = authority.removeSuffix(":443")
        var rest = m.groupValues[3]
        if (rest.isEmpty() || rest.startsWith("?")) rest = "/$rest"
        return "$scheme://$authority$rest"
    }

    fun initialFor(host: String?): String {
        val h = host?.removePrefix("www.").orEmpty()
        val c = h.firstOrNull { it.isLetterOrDigit() } ?: return "•"
        return c.uppercaseChar().toString()
    }
}
