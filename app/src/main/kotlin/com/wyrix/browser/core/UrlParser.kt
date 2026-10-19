package com.wyrix.browser.core

import java.net.URLEncoder

sealed interface Resolved {
    /** [httpFallback] is offered explicitly to the user if an HTTPS-first attempt fails. */
    data class Navigate(val url: String, val httpsFirst: Boolean = false, val httpFallback: String? = null) : Resolved
    data class Search(val query: String, val url: String) : Resolved
    /** A local command such as `wyrix:settings`. */
    data class Internal(val command: String) : Resolved
    data class Blocked(val reason: String) : Resolved
}

/**
 * Turns untrusted address-bar text into a navigation, search, local command, or a refusal.
 * Policy: only http/https are ever navigated from typed text. Everything privileged
 * (javascript:, file:, intent:, data:, ...) is refused (fail closed).
 */
object UrlParser {
    const val MAX_INPUT_LENGTH = 8192

    private val EXPLICIT = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*", RegexOption.DOT_MATCHES_ALL)
    private val SCHEME_COLON = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):.*", RegexOption.DOT_MATCHES_ALL)
    private val BLOCKED_SCHEMES = setOf(
        "javascript", "data", "file", "intent", "vbscript", "blob", "content", "view-source", "about",
        "resource", "chrome", "moz-extension", "jar", "ftp", "mailto", "tel", "sms", "smsto", "market", "geo",
    )
    private const val TLD = "(?:\\p{L}{2,}|xn--[a-zA-Z0-9-]+)"
    private val HOST_LIKE = Regex(
        "^(localhost|(?:\\d{1,3}\\.){3}\\d{1,3}|\\[[0-9a-fA-F:.]+]|(?:[\\p{L}\\p{N}_-]+\\.)+$TLD)" +
            "(?::\\d{1,5})?(?:[/?#].*)?$",
    )
    private val LOCAL_HOST = Regex(
        "^(localhost|(?:\\d{1,3}\\.){3}\\d{1,3}|\\[[0-9a-fA-F:.]+])(?::\\d{1,5})?(?:[/?#].*)?$",
    )

    fun resolve(raw: String, searchTemplate: String, httpsFirst: Boolean = true): Resolved {
        val input = raw.trim()
        if (input.isEmpty()) return Resolved.Blocked("Nothing to open")
        if (input.length > MAX_INPUT_LENGTH) return Resolved.Blocked("That address is too long")
        if (input.any { it.isISOControl() }) return Resolved.Blocked("The address contains control characters")

        if (input.startsWith("wyrix:", ignoreCase = true)) {
            return Resolved.Internal(input.substring(6).trim().lowercase())
        }
        if (EXPLICIT.matches(input)) return explicit(input)

        val scheme = SCHEME_COLON.matchEntire(input)?.groupValues?.get(1)?.lowercase()
        if (scheme != null && scheme in BLOCKED_SCHEMES && input.none { it.isWhitespace() }) {
            return Resolved.Blocked("\"$scheme:\" links can't be opened from the address bar")
        }
        if (input.none { it.isWhitespace() } && HOST_LIKE.matches(input)) {
            return when {
                LOCAL_HOST.matches(input) -> Resolved.Navigate("http://$input")
                httpsFirst -> Resolved.Navigate("https://$input", httpsFirst = true, httpFallback = "http://$input")
                else -> Resolved.Navigate("http://$input")
            }
        }
        return search(input, searchTemplate)
    }

    private fun explicit(input: String): Resolved {
        val scheme = UrlTools.scheme(input) ?: return Resolved.Blocked("Invalid address")
        if (scheme != "http" && scheme != "https") {
            return Resolved.Blocked("\"$scheme://\" addresses are not supported")
        }
        val url = input.replace(" ", "%20")
        if (UrlTools.hostOf(url) == null) return Resolved.Blocked("The address has no host")
        if (UrlTools.hasCredentials(url)) return Resolved.Blocked("Addresses with embedded credentials are not supported")
        return Resolved.Navigate(url)
    }

    private fun search(query: String, template: String): Resolved.Search {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return Resolved.Search(query, template.replace("%s", encoded))
    }
}
