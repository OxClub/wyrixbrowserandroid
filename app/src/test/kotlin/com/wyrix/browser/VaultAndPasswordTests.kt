package com.wyrix.browser

import com.wyrix.browser.core.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.security.SecureRandom

class PasswordGeneratorTest {
    @Test fun respectsLengthAndClasses() {
        repeat(200) {
            val p = PasswordGenerator.generate(GeneratorOptions(length = 12))
            assertEquals(12, p.length)
            assertTrue(p.any { it.isLowerCase() }); assertTrue(p.any { it.isUpperCase() }); assertTrue(p.any { it.isDigit() }); assertTrue(p.any { !it.isLetterOrDigit() })
        }
    }
    @Test fun avoidsAmbiguousCharacters() = repeat(200) { assertTrue(PasswordGenerator.generate(GeneratorOptions(length = 30)).none { it in "O0oIl1|" }) }
    @Test fun clampsLengthAndHandlesNoClassSelected() {
        assertEquals(PasswordGenerator.MIN_LENGTH, PasswordGenerator.generate(GeneratorOptions(length = 1)).length)
        assertEquals(PasswordGenerator.MAX_LENGTH, PasswordGenerator.generate(GeneratorOptions(length = 500)).length)
        assertTrue(PasswordGenerator.generate(GeneratorOptions(lower = false, upper = false, digits = false, symbols = false)).isNotEmpty())
    }
    @Test fun onlyDigitsWhenOnlyDigits() = assertTrue(PasswordGenerator.generate(GeneratorOptions(length = 20, lower = false, upper = false, symbols = false)).all { it.isDigit() })
    @Test fun differentEachTime() = assertNotEquals(PasswordGenerator.generate(GeneratorOptions()), PasswordGenerator.generate(GeneratorOptions(), SecureRandom()))
}

class PasswordStrengthTest {
    @Test fun commonAndShortAreWeak() { assertEquals(Strength.WEAK, PasswordStrength.rate("password")); assertEquals(Strength.WEAK, PasswordStrength.rate("abc12")); assertEquals(Strength.WEAK, PasswordStrength.rate("")) }
    @Test fun repetitionIsPenalised() = assertEquals(Strength.WEAK, PasswordStrength.rate("aaaaaaaaaaaaaaaa"))
    @Test fun generatedPasswordsAreStrong() = assertEquals(Strength.STRONG, PasswordStrength.rate(PasswordGenerator.generate(GeneratorOptions(length = 18))))
    @Test fun mediumIsFair() = assertEquals(Strength.FAIR, PasswordStrength.rate("Sunflower"))
}

class VaultCodecTest {
    private fun item(id: String, pw: String = "pw", host: String = "https://a.example") = LoginItem(id, host, host, "user", pw, "note ü 漢", 1, 2, 3)
    @Test fun roundTrips() {
        val items = listOf(item("1"), item("2", "p\u0000w\"'", "https://b.example"))
        assertEquals(items, VaultCodec.decode(VaultCodec.encode(items)))
    }
    @Test fun emptyRoundTrips() = assertTrue(VaultCodec.decode(VaultCodec.encode(emptyList())).isEmpty())
    @Test fun corruptionIsAnErrorNotEmpty() {
        val good = VaultCodec.encode(listOf(item("1")))
        assertThrows(IOException::class.java) { VaultCodec.decode(good.copyOf(good.size / 2)) }
        assertThrows(IOException::class.java) { VaultCodec.decode(byteArrayOf(9, 0, 0, 0, 0)) }
        assertThrows(IOException::class.java) { VaultCodec.decode(byteArrayOf(1, 127, 0, 0, 0)) }
        assertThrows(IOException::class.java) { VaultCodec.decode(ByteArray(0)) }
    }
}

class LoginMatchingTest {
    private fun item(origin: String, user: String = "u", pw: String = "p", id: String = origin + user) = LoginItem(id, origin, origin, user, pw, "", 0, 0, 0)
    @Test fun exactHostIgnoringWww() {
        val i = item("https://www.example.com")
        assertTrue(LoginMatching.matches(i, "example.com")); assertTrue(LoginMatching.matches(i, "www.example.com")); assertTrue(LoginMatching.matches(i, "EXAMPLE.com"))
    }
    @Test fun siblingAndParentDomainsNeverMatch() {
        val i = item("https://example.com")
        assertFalse(LoginMatching.matches(i, "evil.example.com")); assertFalse(LoginMatching.matches(i, "example.com.evil.net")); assertFalse(LoginMatching.matches(i, "notexample.com")); assertFalse(LoginMatching.matches(i, ""))
        assertFalse(LoginMatching.matches(item("https://app.example.com"), "example.com"))
    }
    @Test fun sameAccountDetection() {
        val i = item("https://example.com", "bob")
        assertTrue(LoginMatching.sameAccount(i, "https://www.example.com/login", "bob")); assertFalse(LoginMatching.sameAccount(i, "https://example.com", "alice")); assertFalse(LoginMatching.sameAccount(i, "https://other.com", "bob"))
    }
    @Test fun reuseDetection() {
        val a = item("https://a.com", pw = "same"); val b = item("https://b.com", pw = "same"); val c = item("https://c.com", pw = "unique"); val d = item("https://d.com", pw = "")
        assertEquals(setOf(a.id, b.id), LoginMatching.reusedIds(listOf(a, b, c, d)))
    }
}

class LoginCsvTest {
    @Test fun parsesChromeFormatWithQuotesAndNewlines() {
        val csv = "name,url,username,password,note\n" +
            "ex,https://example.com/login,bob,\"p,\"\"w\"\"\nd\",\"multi\nline\"\r\n" +
            "bad,javascript:1,x,y,\n" + "creds,https://u:p@e.com,x,y,\n" + "nopw,https://e.com,x,,\n"
        val r = LoginCsv.parse(csv)
        assertEquals(1, r.logins.size); assertEquals(3, r.skipped)
        with(r.logins[0]) { assertEquals("bob", username); assertEquals("p,\"w\"\nd", password); assertEquals("multi\nline", note) }
    }
    @Test fun parsesFirefoxStyleHeaderAndBom() {
        val r = LoginCsv.parse("\uFEFF\"url\",\"username\",\"password\",\"httpRealm\"\n\"https://a.example\",\"u\",\"p\",\"\"\n")
        assertEquals(1, r.logins.size); assertEquals("p", r.logins[0].password)
    }
    @Test fun unknownHeaderYieldsNothing() = assertTrue(LoginCsv.parse("foo,bar\n1,2\n").logins.isEmpty())
    @Test fun emptyAndGarbage() { assertTrue(LoginCsv.parse("").logins.isEmpty()); assertTrue(LoginCsv.parse("\u0000\u0001").logins.isEmpty()) }
    @Test fun rowLimit() {
        val csv = "url,username,password\n" + (1..LoginCsv.MAX_ROWS + 10).joinToString("\n") { "https://e$it.com,u,p" }
        val r = LoginCsv.parse(csv); assertEquals(LoginCsv.MAX_ROWS, r.logins.size); assertTrue(r.truncated)
    }
    @Test fun writeAndReadBackPreservesTrickyPasswordsExactly() {
        val items = listOf(CsvLogin("https://a.example", "u,1", "=SUM(1)+\"x\"\n@y", "n"), CsvLogin("https://b.example", "", "-leading", ""))
        val back = LoginCsv.parse(LoginCsv.write(items)).logins
        assertEquals(items, back)
    }
}

class QrClassifierTest {
    @Test fun webUrl() { val c = QrClassifier.classify("  https://www.example.com/a?b=1 ") as QrContent.Web; assertEquals("www.example.com", c.host) }
    @Test fun dangerousSchemesAreJustText() {
        assertTrue(QrClassifier.classify("javascript:alert(1)") is QrContent.Text); assertTrue(QrClassifier.classify("file:///etc/passwd") is QrContent.Text)
        assertTrue(QrClassifier.classify("intent://x#Intent;end") is QrContent.Text); assertTrue(QrClassifier.classify("https://a.com@evil.com/") is QrContent.Text)
    }
    @Test fun wifiParsedWithEscapes() {
        val c = QrClassifier.classify("WIFI:T:WPA;S:my\\;net;P:pa\\\\ss;;") as QrContent.Wifi
        assertEquals("my;net", c.ssid); assertEquals("pa\\ss", c.password); assertEquals("WPA", c.security)
    }
    @Test fun wifiWithoutSecurity() = assertEquals("nopass", (QrClassifier.classify("WIFI:S:cafe;;") as QrContent.Wifi).security)
    @Test fun contactsAndText() {
        assertTrue(QrClassifier.classify("mailto:a@b.c") is QrContent.Contact); assertTrue(QrClassifier.classify("tel:+123") is QrContent.Contact)
        assertTrue(QrClassifier.classify("BEGIN:VCARD\nFN:A") is QrContent.Contact); assertTrue(QrClassifier.classify("hello world") is QrContent.Text)
    }
    @Test fun controlCharactersInUrlDemotedToText() = assertTrue(QrClassifier.classify("https://a.example/\u0000x") is QrContent.Text)
}
