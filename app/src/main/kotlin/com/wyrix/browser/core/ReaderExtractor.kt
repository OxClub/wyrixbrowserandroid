package com.wyrix.browser.core

sealed interface ReaderBlock {
    val text: String
    data class Heading(val level: Int, override val text: String) : ReaderBlock
    data class Paragraph(override val text: String) : ReaderBlock
    data class Quote(override val text: String) : ReaderBlock
    data class Item(override val text: String) : ReaderBlock
    data class Code(override val text: String) : ReaderBlock
}

data class ReaderArticle(
    val url: String,
    val title: String,
    val siteName: String,
    val blocks: List<ReaderBlock>,
) {
    val wordCount: Int get() = blocks.sumOf { it.text.split(Regex("\\s+")).count { w -> w.isNotBlank() } }
    val minutes: Int get() = maxOf(1, (wordCount + 219) / 220)
}

/**
 * Heuristic article extractor for static HTML (no DOM library, no JavaScript). It keeps headings, paragraphs,
 * quotes, list items and code from the main content region and drops navigation, scripts, forms, link farms and
 * very short fragments. Pages rendered by JavaScript, paywalled pages and non-articles return null.
 */
object ReaderExtractor {
    const val MAX_HTML_CHARS = 1_500_000
    private const val MAX_BLOCKS = 1500

    private val TITLE_OG = Regex("(?is)<meta[^>]+property\\s*=\\s*[\"']og:title[\"'][^>]*content\\s*=\\s*[\"']([^\"']*)[\"']")
    private val TITLE_OG2 = Regex("(?is)<meta[^>]+content\\s*=\\s*[\"']([^\"']*)[\"'][^>]*property\\s*=\\s*[\"']og:title[\"']")
    private val SITE_OG = Regex("(?is)<meta[^>]+property\\s*=\\s*[\"']og:site_name[\"'][^>]*content\\s*=\\s*[\"']([^\"']*)[\"']")
    private val TITLE_TAG = Regex("(?is)<title[^>]*>(.*?)</title>")
    private val H1 = Regex("(?is)<h1[^>]*>(.*?)</h1>")
    private val ARTICLE = Regex("(?is)<article\\b[^>]*>(.*?)</article>")
    private val MAIN = Regex("(?is)<main\\b[^>]*>(.*?)</main>")
    private val BODY = Regex("(?is)<body\\b[^>]*>(.*)</body>")
    private val BLOCK = Regex("(?is)<(h[1-4]|p|blockquote|li|pre)\\b[^>]*>(.{0,30000}?)</\\1\\s*>")
    private val ANCHOR = Regex("(?is)<a\\b[^>]*>(.*?)</a>")
    private val TAGS = Regex("(?s)<[^>]*>")
    private val WS = Regex("\\s+")
    private val NUMERIC = Regex("&#(x?)([0-9a-fA-F]{1,6});")

    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "mdash" to "—", "ndash" to "–",
        "hellip" to "…", "rsquo" to "’", "lsquo" to "‘", "ldquo" to "“", "rdquo" to "”", "copy" to "©", "laquo" to "«", "raquo" to "»",
    )

    fun decode(s: String): String {
        var out = Regex("&([a-zA-Z]{2,8});").replace(s) { NAMED[it.groupValues[1]] ?: it.value }
        out = NUMERIC.replace(out) {
            val cp = it.groupValues[2].toIntOrNull(if (it.groupValues[1].isEmpty()) 10 else 16)
            if (cp != null && cp in 32..0x10FFFF && cp !in 0xD800..0xDFFF) String(Character.toChars(cp)) else ""
        }
        return out
    }

    private val DROP_TAGS = listOf("script", "style", "noscript", "svg", "form", "nav", "footer", "aside", "header", "iframe", "template", "button", "select", "dialog")

    /** Linear-time comment removal; an unclosed comment swallows the rest, as browsers do. */
    private fun removeComments(s: String): String {
        if (!s.contains("<!--")) return s
        val sb = StringBuilder(s.length); var pos = 0
        while (true) {
            val start = s.indexOf("<!--", pos)
            if (start < 0) { sb.append(s, pos, s.length); break }
            sb.append(s, pos, start)
            val end = s.indexOf("-->", start + 4)
            if (end < 0) break
            pos = end + 3
        }
        return sb.toString()
    }

    /**
     * Removes `<tag ...>...</tag>` blocks without regex backtracking. Once one opening tag has no closer, no later
     * one can have one either, so remaining openers only lose their tag; the scan stays O(n) per tag name.
     */
    private fun removeBlocks(s: String, tag: String): String {
        val open = "<$tag"; val close = "</$tag"
        if (!s.contains(open, ignoreCase = true)) return s
        val sb = StringBuilder(s.length); var pos = 0; var noClosers = false
        while (true) {
            var start = s.indexOf(open, pos, ignoreCase = true)
            while (start >= 0 && start + open.length < s.length && s[start + open.length].let { it != '>' && it != '/' && !it.isWhitespace() }) {
                start = s.indexOf(open, start + 1, ignoreCase = true)
            }
            if (start < 0) { sb.append(s, pos, s.length); break }
            sb.append(s, pos, start)
            val openEnd = s.indexOf('>', start).let { if (it < 0) s.length else it + 1 }
            val closeIdx = if (noClosers) -1 else s.indexOf(close, openEnd, ignoreCase = true)
            if (closeIdx < 0) { noClosers = true; pos = openEnd; continue }
            val closeEnd = s.indexOf('>', closeIdx).let { if (it < 0) s.length else it + 1 }
            sb.append(' '); pos = closeEnd
        }
        return sb.toString()
    }

    private fun textOf(html: String): String = WS.replace(decode(TAGS.replace(html, " ")), " ").trim()

    fun extract(html: String, url: String): ReaderArticle? {
        if (html.isBlank()) return null
        val src = if (html.length > MAX_HTML_CHARS) html.take(MAX_HTML_CHARS) else html
        val title = (TITLE_OG.find(src) ?: TITLE_OG2.find(src))?.groupValues?.get(1)?.let { textOf(it) }?.takeIf { it.isNotBlank() }
            ?: TITLE_TAG.find(src)?.groupValues?.get(1)?.let { textOf(it) }?.takeIf { it.isNotBlank() }
            ?: H1.find(src)?.groupValues?.get(1)?.let { textOf(it) }.orEmpty()
        val site = SITE_OG.find(src)?.groupValues?.get(1)?.let { textOf(it) }?.takeIf { it.isNotBlank() } ?: (UrlTools.hostOf(url)?.removePrefix("www.") ?: "")

        var cleaned = removeComments(src)
        for (tag in DROP_TAGS) cleaned = removeBlocks(cleaned, tag)
        val articles = if (cleaned.contains("</article", ignoreCase = true)) ARTICLE.findAll(cleaned).map { it.groupValues[1] }.toList() else emptyList()
        val main = if (cleaned.contains("</main", ignoreCase = true)) MAIN.find(cleaned)?.groupValues?.get(1) else null
        val body = if (cleaned.contains("</body", ignoreCase = true)) BODY.find(cleaned)?.groupValues?.get(1) else null
        val region = articles.maxByOrNull { it.length } ?: main ?: body ?: cleaned
        val structured = articles.isNotEmpty() || main != null

        val blocks = ArrayList<ReaderBlock>()
        var last = ""
        for (m in BLOCK.findAll(region)) {
            if (blocks.size >= MAX_BLOCKS) break
            val tag = m.groupValues[1].lowercase()
            val inner = m.groupValues[2]
            val text = textOf(inner)
            if (text.isEmpty()) continue
              if (text == last) {
                  last = ""
                  continue
              }
            val linkChars = ANCHOR.findAll(inner).sumOf { textOf(it.groupValues[1]).length }
            val linkHeavy = text.isNotEmpty() && linkChars.toDouble() / text.length > 0.5 && text.length < 200
            when (tag) {
                "h1", "h2", "h3", "h4" -> if (text.length in 3..220 && !linkHeavy) blocks += ReaderBlock.Heading(tag[1] - '0', text)
                "p" -> if (text.length >= 40 && !linkHeavy) blocks += ReaderBlock.Paragraph(text)
                "blockquote" -> if (text.length >= 20) blocks += ReaderBlock.Quote(text)
                "li" -> if (text.length >= 25 && !linkHeavy) blocks += ReaderBlock.Item(text)
                "pre" -> if (text.length >= 10) blocks += ReaderBlock.Code(decode(TAGS.replace(inner, "")).trim().take(4000))
            }
            last = text
        }
        val longParas = blocks.count { it is ReaderBlock.Paragraph && it.text.length >= 80 }
        if (longParas < (if (structured) 2 else 4)) return null
        // A leading heading equal to the title would be shown twice.
        val trimmed = blocks.dropWhile { it is ReaderBlock.Heading && it.text.equals(title, ignoreCase = true) }
        return ReaderArticle(url, title.ifBlank { site }, site, trimmed)
    }
}
