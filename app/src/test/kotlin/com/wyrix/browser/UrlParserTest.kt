package com.wyrix.browser

import com.wyrix.browser.core.Resolved
import com.wyrix.browser.core.UrlParser
import org.junit.Assert.*
import org.junit.Test

class UrlParserTest {
    private val tmpl = "https://duckduckgo.com/?q=%s"
    private fun r(s: String, httpsFirst: Boolean = true) = UrlParser.resolve(s, tmpl, httpsFirst)

    @Test fun bareDomainUsesHttpsWithExplicitFallback() {
        val x = r("example.com") as Resolved.Navigate
        assertEquals("https://example.com", x.url); assertTrue(x.httpsFirst); assertEquals("http://example.com", x.httpFallback)
    }
    @Test fun httpsFirstDisabledUsesHttp() = assertEquals("http://example.com", (r("example.com", false) as Resolved.Navigate).url)
    @Test fun explicitHttpsKept() = assertEquals("https://a.b/c?d=e", (r("https://a.b/c?d=e") as Resolved.Navigate).url)
    @Test fun explicitHttpKeptWithoutUpgradeFlag() { val x = r("http://a.b") as Resolved.Navigate; assertEquals("http://a.b", x.url); assertFalse(x.httpsFirst) }
    @Test fun localhostAndIpUseHttp() {
        assertEquals("http://localhost:8080/x", (r("localhost:8080/x") as Resolved.Navigate).url)
        assertEquals("http://192.168.1.1", (r("192.168.1.1") as Resolved.Navigate).url)
    }
    @Test fun domainWithPortAndPath() = assertEquals("https://example.org:8443/a", (r("example.org:8443/a") as Resolved.Navigate).url)
    @Test fun plainWordsSearch() {
        val x = r("best pizza near me") as Resolved.Search
        assertEquals("https://duckduckgo.com/?q=best+pizza+near+me", x.url)
    }
    @Test fun singleWordSearches() = assertTrue(r("kotlin") is Resolved.Search)
    @Test fun queryIsPercentEncoded() = assertEquals("https://duckduckgo.com/?q=a%26b%3Dc+%25", (r("a&b=c %") as Resolved.Search).url)
    @Test fun unicodeDomainNavigates() = assertTrue(r("пример.рф") is Resolved.Navigate)
    @Test fun punycodeTldNavigates() = assertTrue(r("example.xn--p1ai") is Resolved.Navigate)
    @Test fun dangerousSchemesBlocked() {
        listOf("javascript:alert(1)", "data:text/html,<b>x", "file:///etc/passwd", "intent://x#Intent;end", "vbscript:x", "blob:https://a/b", "view-source:https://a.b", "about:config", "mailto:a@b.c", "tel:123", "content://x")
            .forEach { assertTrue("$it should be blocked", r(it) is Resolved.Blocked) }
    }
    @Test fun dangerousExplicitSchemesBlocked() {
        listOf("ftp://x.y/z", "file://x", "javascript://%0aalert(1)", "chrome://settings", "resource://x").forEach { assertTrue("$it", r(it) is Resolved.Blocked) }
    }
    @Test fun schemeLikeSentenceIsSearchNotBlocked() = assertTrue(r("tel: what is this") is Resolved.Search)
    @Test fun emptyAndWhitespaceBlocked() { assertTrue(r("") is Resolved.Blocked); assertTrue(r("   ") is Resolved.Blocked) }
    @Test fun controlCharactersBlocked() = assertTrue(r("exa\u0000mple.com") is Resolved.Blocked)
    @Test fun overlongInputBlocked() = assertTrue(r("a".repeat(UrlParser.MAX_INPUT_LENGTH + 1)) is Resolved.Blocked)
    @Test fun credentialsInUrlBlocked() = assertTrue(r("https://google.com@evil.example/") is Resolved.Blocked)
    @Test fun spacesInExplicitUrlEncoded() = assertEquals("https://a.b/x%20y", (r("https://a.b/x y") as Resolved.Navigate).url)
    @Test fun internalCommand() = assertEquals("settings", (r("wyrix:Settings") as Resolved.Internal).command)
    @Test fun noHostBlocked() = assertTrue(r("https://") is Resolved.Blocked)
    @Test fun trailingDotAndNumbersAreSearch() = assertTrue(r("3.14") is Resolved.Search)
    @Test fun ipv6Literal() = assertTrue(r("[::1]:8080") is Resolved.Navigate)
}
