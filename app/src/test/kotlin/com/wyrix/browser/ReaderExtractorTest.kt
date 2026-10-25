package com.wyrix.browser

import com.wyrix.browser.core.ReaderBlock
import com.wyrix.browser.core.ReaderExtractor
import org.junit.Assert.*
import org.junit.Test

class ReaderExtractorTest {
    private val para = "This is a reasonably long paragraph of article text that easily exceeds the minimum length threshold for the extractor."
    private fun page(body: String, head: String = "<title>Fallback &amp; Title</title>") = "<html><head>$head</head><body>$body</body></html>"

    @Test fun extractsArticleAndDropsChrome() {
        val html = page("""
            <header><h1>Site header</h1><p>$para menu junk</p></header>
            <nav><ul><li><a href="/a">Home page link long enough to count</a></li></ul></nav>
            <article><h1>Real Title</h1><h2>Section one</h2><p>$para</p><p>Second &quot;quoted&quot; paragraph. $para</p>
              <blockquote>A memorable quote that is long enough.</blockquote><script>var x = "<p>$para</p>";</script></article>
            <footer><p>$para footer junk</p></footer>
        """, "<meta property=\"og:title\" content=\"OG Title\"><meta property=\"og:site_name\" content=\"The Site\"><title>ignored</title>")
        val a = ReaderExtractor.extract(html, "https://example.com/a")!!
        assertEquals("OG Title", a.title); assertEquals("The Site", a.siteName)
        assertTrue(a.blocks.any { it is ReaderBlock.Heading && it.text == "Section one" })
        assertEquals(2, a.blocks.count { it is ReaderBlock.Paragraph })
        assertTrue(a.blocks.any { it is ReaderBlock.Quote })
        assertTrue(a.blocks.none { it.text.contains("junk") || it.text.contains("var x") })
        assertTrue(a.blocks.any { it.text.contains("\"quoted\"") })
    }
    @Test fun fallsBackToTitleTagAndHost() {
        val a = ReaderExtractor.extract(page("<main><p>$para</p><p>$para two</p></main>"), "https://www.example.org/x")!!
        assertEquals("Fallback & Title", a.title); assertEquals("example.org", a.siteName)
    }
    @Test fun nonArticlePagesReturnNull() {
        assertNull(ReaderExtractor.extract(page("<p>short</p><ul><li><a href=\"/x\">link list item number one</a></li></ul>"), "https://e.com"))
        assertNull(ReaderExtractor.extract("", "https://e.com"))
    }
    @Test fun unstructuredPageNeedsMoreParagraphs() {
        assertNull(ReaderExtractor.extract(page("<p>$para</p><p>$para two</p>"), "https://e.com"))
        assertNotNull(ReaderExtractor.extract(page("<p>$para</p><p>$para two</p><p>$para three</p><p>$para four</p>"), "https://e.com"))
    }
    @Test fun linkHeavyBlocksDropped() {
        val html = page("<article><p>$para</p><p>$para two</p><p><a href=\"/1\">Related story link text here</a> <a href=\"/2\">Another related link here</a></p></article>")
        assertEquals(2, ReaderExtractor.extract(html, "https://e.com")!!.blocks.size)
    }
    @Test fun decodesEntities() {
        assertEquals("a & b < c — d ’ é A", ReaderExtractor.decode("a &amp; b &lt; c &mdash; d &rsquo; &#233; &#x41;"))
        assertEquals("", ReaderExtractor.decode("&#xD800;"))
    }
    @Test fun readingTimeAndDedupe() {
        val a = ReaderExtractor.extract(page("<article><p>$para</p><p>$para</p><p>$para two</p><p>$para three</p></article>"), "https://e.com")!!
        assertEquals(3, a.blocks.size); assertTrue(a.minutes >= 1); assertTrue(a.wordCount > 30)
    }
    @Test fun hugeInputIsBounded() {
        val html = page("<article>" + (1..200_000).joinToString("") { "<p>$para $it</p>" } + "</article>")
        val a = ReaderExtractor.extract(html, "https://e.com")
        assertNotNull(a); assertTrue(a!!.blocks.size <= 1500)
    }
}
