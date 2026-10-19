package com.wyrix.browser

import com.wyrix.browser.core.UrlTools
import org.junit.Assert.*
import org.junit.Test

class UrlToolsTest {
    @Test fun hostOfBasic() = assertEquals("example.com", UrlTools.hostOf("https://Example.com:8080/x"))
    @Test fun hostOfIgnoresUserInfo() = assertEquals("evil.example", UrlTools.hostOf("https://google.com@evil.example/"))
    @Test fun hostOfIpv6() = assertEquals("[::1]", UrlTools.hostOf("http://[::1]:80/"))
    @Test fun hostOfNone() = assertNull(UrlTools.hostOf("not a url"))
    @Test fun isWebUrl() { assertTrue(UrlTools.isWebUrl("https://a.b")); assertFalse(UrlTools.isWebUrl("javascript:1")); assertFalse(UrlTools.isWebUrl("https://")) }
    @Test fun normalizeLowercasesAndDropsFragmentAndDefaultPort() = assertEquals("https://example.com/a", UrlTools.normalize("HTTPS://Example.com:443/a#frag"))
    @Test fun normalizeAddsRootSlash() { assertEquals("https://a.b/", UrlTools.normalize("https://a.b")); assertEquals("https://a.b/?q=1", UrlTools.normalize("https://a.b?q=1")) }
    @Test fun normalizeKeepsNonDefaultPortAndPathCase() = assertEquals("http://a.b:8080/Path", UrlTools.normalize("http://a.b:8080/Path"))
    @Test fun hostPartsSimple() { val p = UrlTools.hostParts("https://www.example.com/")!!; assertEquals("www", p.subdomain); assertEquals("example.com", p.domain) }
    @Test fun hostPartsSecondLevelCountryDomain() { val p = UrlTools.hostParts("https://news.bbc.co.uk/")!!; assertEquals("news", p.subdomain); assertEquals("bbc.co.uk", p.domain) }
    @Test fun hostPartsIpHasNoSubdomain() { val p = UrlTools.hostParts("http://10.0.0.1/")!!; assertEquals("", p.subdomain); assertEquals("10.0.0.1", p.domain) }
    @Test fun hostPartsFlagsPunycode() { val p = UrlTools.hostParts("https://xn--e1afmkfd.xn--p1ai/")!!; assertTrue(p.isPunycode); assertNotNull(p.unicodeHint) }
    @Test fun initials() { assertEquals("E", UrlTools.initialFor("www.example.com")); assertEquals("•", UrlTools.initialFor(null)) }
}
