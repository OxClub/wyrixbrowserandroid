package com.wyrix.browser.core

data class ParsedBookmark(val title: String, val url: String, val folderPath: List<String>, val addDate: Long?)
data class ParseResult(val bookmarks: List<ParsedBookmark>, val skipped: Int, val truncated: Boolean)
data class ExportItem(val title: String, val url: String, val addDateSeconds: Long)
data class ExportFolder(val name: String, val folders: List<ExportFolder>, val items: List<ExportItem>)

/** Netscape bookmark file format reader/writer (the de-facto interchange format). */
object BookmarkHtml {
    const val MAX_BYTES = 5 * 1024 * 1024
    const val MAX_ITEMS = 20_000
    private const val MAX_DEPTH = 32

    private val TOKEN = Regex("(?is)<h3[^>]*>(.*?)</h3>|<a\\s([^>]*)>(.*?)</a>|<dl[^>]*>|</dl>")
    private val HREF = Regex("(?i)href\\s*=\\s*\"([^\"]*)\"")
    private val ADD_DATE = Regex("(?i)add_date\\s*=\\s*\"(\\d{1,12})\"")
    private val TAGS = Regex("<[^>]*>")

    fun parse(html: String): ParseResult {
        val path = ArrayList<String>()
        val opened = ArrayDeque<Boolean>()
        var pending: String? = null
        val out = ArrayList<ParsedBookmark>()
        var skipped = 0
        var truncated = false
        for (m in TOKEN.findAll(html)) {
            val t = m.value
            when {
                t.startsWith("<h3", true) -> pending = decode(TAGS.replace(m.groupValues[1], "")).trim()
                t.startsWith("<a", true) -> {
                    val href = HREF.find(m.groupValues[2])?.groupValues?.get(1)?.let { decode(it).trim() }
                    if (href == null || !UrlTools.isWebUrl(href) || UrlTools.hasCredentials(href)) {
                        skipped++
                    } else if (out.size >= MAX_ITEMS) {
                        truncated = true
                        break
                    } else {
                        val title = decode(TAGS.replace(m.groupValues[3], "")).trim()
                            .ifEmpty { UrlTools.hostOf(href).orEmpty() }.take(300)
                        val date = ADD_DATE.find(m.groupValues[2])?.groupValues?.get(1)?.toLongOrNull()
                        out += ParsedBookmark(title, href, path.toList(), date)
                    }
                }
                t.startsWith("<dl", true) -> {
                    val name = pending?.takeIf { it.isNotEmpty() && path.size < MAX_DEPTH }
                    if (name != null) { path += name; opened.addLast(true) } else opened.addLast(false)
                    pending = null
                }
                else -> if (opened.isNotEmpty() && opened.removeLast()) path.removeAt(path.lastIndex)
            }
        }
        return ParseResult(out, skipped, truncated)
    }

    fun export(root: ExportFolder): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n")
        sb.append("<!-- This is an automatically generated file by WYRIX Browser. -->\n")
        sb.append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
        sb.append("<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n")
        writeFolderBody(sb, root, 0)
        return sb.toString()
    }

    private fun writeFolderBody(sb: StringBuilder, f: ExportFolder, depth: Int) {
        val pad = "    ".repeat(depth)
        sb.append(pad).append("<DL><p>\n")
        for (sub in f.folders) {
            sb.append(pad).append("    <DT><H3>").append(escape(sub.name)).append("</H3>\n")
            writeFolderBody(sb, sub, depth + 1)
        }
        for (i in f.items) {
            sb.append(pad).append("    <DT><A HREF=\"").append(escape(i.url)).append("\" ADD_DATE=\"")
                .append(i.addDateSeconds).append("\">").append(escape(i.title)).append("</A>\n")
        }
        sb.append(pad).append("</DL><p>\n")
    }

    fun escape(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;")

    fun decode(s: String): String = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&")
}
