package com.wyrix.browser.core

import java.net.URLDecoder

/** Turns server-supplied names into safe single-segment file names. */
object FilenameSanitizer {
    const val MAX_LENGTH = 120
    private val ILLEGAL = Regex("[\\\\/:*?\"<>|\\p{Cntrl}\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]")
    private val RESERVED = setOf(
        "con", "prn", "aux", "nul", "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
        "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9",
    )
    private val MIME_EXT = mapOf(
        "application/pdf" to "pdf", "image/png" to "png", "image/jpeg" to "jpg", "image/gif" to "gif",
        "image/webp" to "webp", "text/plain" to "txt", "text/html" to "html", "application/zip" to "zip",
        "application/json" to "json", "video/mp4" to "mp4", "audio/mpeg" to "mp3",
    )

    fun sanitize(raw: String?, urlFallback: String? = null, mime: String? = null): String {
        var name = clean(raw)
        if (name.isEmpty() && urlFallback != null) {
            val path = urlFallback.substringBefore('#').substringBefore('?').substringAfter("://", urlFallback)
            val last = path.substringAfter('/', "").substringAfterLast('/')
            name = clean(runCatching { URLDecoder.decode(last, "UTF-8") }.getOrDefault(last))
        }
        if (name.isEmpty()) {
            name = "download" + (MIME_EXT[mime?.substringBefore(';')?.trim()?.lowercase()]?.let { ".$it" } ?: "")
        }
        return limit(name)
    }

    private fun clean(raw: String?): String {
        var s = raw.orEmpty().trim()
        s = s.substringAfterLast('/').substringAfterLast('\\') // defeats ../ traversal
        s = ILLEGAL.replace(s, "_")
        s = s.replace(Regex("\\s+"), " ")
        s = s.replace(Regex("\\.{2,}"), ".")
        s = s.trim(' ', '.')
        if (s.isEmpty() || s.all { it == '_' }) return ""
        if (s.substringBefore('.').lowercase() in RESERVED) s = "_$s"
        return s
    }

    private fun limit(name: String): String {
        if (name.length <= MAX_LENGTH) return name
        val dot = name.lastIndexOf('.')
        val ext = if (dot > 0 && name.length - dot <= 11) name.substring(dot) else ""
        return name.take(MAX_LENGTH - ext.length) + ext
    }

    /** Extracts a file name from a Content-Disposition header (RFC 6266 / 5987), unsanitised. */
    fun fromContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        Regex("filename\\*\\s*=\\s*([^']*)'[^']*'([^;]+)", RegexOption.IGNORE_CASE).find(header)?.let {
            val charset = it.groupValues[1].ifBlank { "UTF-8" }
            return runCatching { URLDecoder.decode(it.groupValues[2].trim().trim('"'), charset) }.getOrNull()
        }
        Regex("filename\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(header)?.let { return it.groupValues[1] }
        Regex("filename\\s*=\\s*([^;]+)", RegexOption.IGNORE_CASE).find(header)?.let { return it.groupValues[1].trim() }
        return null
    }
}
