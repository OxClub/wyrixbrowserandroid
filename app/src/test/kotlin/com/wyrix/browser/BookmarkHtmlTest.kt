package com.wyrix.browser

import com.wyrix.browser.core.*
import org.junit.Assert.*
import org.junit.Test

class BookmarkHtmlTest {
    private val sample = """
        <!DOCTYPE NETSCAPE-Bookmark-file-1>
        <DL><p>
          <DT><H3>News &amp; Blogs</H3>
          <DL><p>
            <DT><A HREF="https://example.com/a?x=1&amp;y=2" ADD_DATE="1700000000">Example &lt;A&gt;</A>
            <DT><H3>Deep</H3>
            <DL><p><DT><A HREF="https://deep.example/">Deep one</A></DL><p>
          </DL><p>
          <DT><A HREF="https://root.example/">Root</A>
          <DT><A HREF="javascript:alert(1)">Bad</A>
          <DT><A HREF="file:///etc/passwd">Bad2</A>
          <DT><A HREF="https://user:pw@evil.example/">Creds</A>
        </DL><p>
    """.trimIndent()

    @Test fun parsesFoldersAndDecodesEntities() {
        val r = BookmarkHtml.parse(sample)
        assertEquals(3, r.bookmarks.size)
        val a = r.bookmarks.first { it.url.startsWith("https://example.com") }
        assertEquals("https://example.com/a?x=1&y=2", a.url); assertEquals("Example <A>", a.title)
        assertEquals(listOf("News & Blogs"), a.folderPath); assertEquals(1700000000L, a.addDate)
        assertEquals(listOf("News & Blogs", "Deep"), r.bookmarks.first { it.title == "Deep one" }.folderPath)
        assertEquals(emptyList<String>(), r.bookmarks.first { it.title == "Root" }.folderPath)
    }
    @Test fun rejectsNonWebAndCredentialUrls() = assertEquals(3, BookmarkHtml.parse(sample).skipped)
    @Test fun garbageInputYieldsNothing() { val r = BookmarkHtml.parse("<<<not html>>>\u0000"); assertTrue(r.bookmarks.isEmpty()) }
    @Test fun emptyInput() = assertTrue(BookmarkHtml.parse("").bookmarks.isEmpty())
    @Test fun itemLimitEnforced() {
        val big = buildString { repeat(BookmarkHtml.MAX_ITEMS + 5) { append("<DT><A HREF=\"https://e.com/$it\">t$it</A>\n") } }
        val r = BookmarkHtml.parse(big)
        assertEquals(BookmarkHtml.MAX_ITEMS, r.bookmarks.size); assertTrue(r.truncated)
    }
    @Test fun exportEscapesAndRoundTrips() {
        val tree = ExportFolder("Bookmarks", listOf(ExportFolder("A & B", emptyList(), listOf(ExportItem("t\"<>", "https://x.y/?a=1&b=2", 5)))), listOf(ExportItem("root", "https://r.s/", 9)))
        val html = BookmarkHtml.export(tree)
        assertTrue(html.contains("A &amp; B")); assertFalse(html.contains("t\"<>"))
        val back = BookmarkHtml.parse(html)
        assertEquals(2, back.bookmarks.size)
        val x = back.bookmarks.first { it.url.contains("x.y") }
        assertEquals("https://x.y/?a=1&b=2", x.url); assertEquals("t\"<>", x.title); assertEquals(listOf("A & B"), x.folderPath)
    }
}
