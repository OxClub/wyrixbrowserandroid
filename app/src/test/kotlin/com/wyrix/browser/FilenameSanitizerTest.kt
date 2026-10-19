package com.wyrix.browser

import com.wyrix.browser.core.FilenameSanitizer
import org.junit.Assert.*
import org.junit.Test

class FilenameSanitizerTest {
    private fun s(x: String?, u: String? = null, m: String? = null) = FilenameSanitizer.sanitize(x, u, m)
    @Test fun plainNameKept() = assertEquals("report.pdf", s("report.pdf"))
    @Test fun traversalStripped() { assertEquals("passwd", s("../../etc/passwd")); assertEquals("evil.exe", s("..\\..\\evil.exe")) }
    @Test fun illegalCharsReplaced() = assertEquals("a_b_c.txt", s("a:b*c.txt"))
    @Test fun leadingDotsRemoved() = assertEquals("hidden", s(".hidden"))
    @Test fun repeatedDotsCollapsed() = assertEquals("a.b", s("a....b"))
    @Test fun bidiOverrideRemoved() = assertFalse(s("invoice\u202Efdp.exe").contains('\u202E'))
    @Test fun controlCharsRemoved() = assertFalse(s("a\u0000b\nc").any { it.isISOControl() })
    @Test fun reservedWindowsNamePrefixed() = assertEquals("_con.txt", s("con.txt"))
    @Test fun blankFallsBackToUrlSegment() = assertEquals("file.zip", s("", "https://host/dir/file.zip?x=1"))
    @Test fun blankAndNoUrlUsesMimeExtension() = assertEquals("download.pdf", s(null, null, "application/pdf; charset=x"))
    @Test fun blankNoInfoGeneric() = assertEquals("download", s(null))
    @Test fun allIllegalBecomesGeneric() = assertEquals("download", s("***"))
    @Test fun longNamesTruncatedPreservingExtension() {
        val n = s("a".repeat(500) + ".tar")
        assertEquals(FilenameSanitizer.MAX_LENGTH, n.length); assertTrue(n.endsWith(".tar"))
    }
    @Test fun contentDispositionQuoted() = assertEquals("a b.pdf", FilenameSanitizer.fromContentDisposition("attachment; filename=\"a b.pdf\""))
    @Test fun contentDispositionRfc5987() = assertEquals("é.txt", FilenameSanitizer.fromContentDisposition("attachment; filename*=UTF-8''%C3%A9.txt"))
    @Test fun contentDispositionBare() = assertEquals("x.bin", FilenameSanitizer.fromContentDisposition("attachment; filename=x.bin"))
    @Test fun contentDispositionMissing() = assertNull(FilenameSanitizer.fromContentDisposition("inline"))
}
