package com.wyrix.browser.core

enum class LoadDecision { ALLOW, CONFIRM_EXTERNAL, DENY }

/**
 * Policy for navigations requested by web content (not by the address bar).
 * - Web schemes load normally; Gecko itself blocks top-level data: navigations from content.
 * - javascript: links run in the page's own context (same as any script), so they are allowed here.
 *   Typed javascript: is refused by [UrlParser].
 * - Other apps are only launched after an explicit user confirmation.
 * - file:, content:, resource:, chrome:, view-source: etc. are denied outright.
 */
object LoadPolicy {
    private val ALLOWED = setOf("http", "https", "blob", "data", "javascript")
    private val EXTERNAL = setOf("intent", "mailto", "tel", "sms", "smsto", "market", "geo")

    fun decide(uri: String): LoadDecision {
        val scheme = UrlTools.scheme(uri) ?: return LoadDecision.DENY
        return when {
            scheme in ALLOWED -> LoadDecision.ALLOW
            scheme == "about" -> if (uri.equals("about:blank", ignoreCase = true)) LoadDecision.ALLOW else LoadDecision.DENY
            scheme in EXTERNAL -> LoadDecision.CONFIRM_EXTERNAL
            else -> LoadDecision.DENY
        }
    }
}
