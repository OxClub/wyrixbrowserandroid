package com.wyrix.browser.core

import java.security.SecureRandom
import kotlin.math.log2

data class GeneratorOptions(
    val length: Int = 18,
    val lower: Boolean = true,
    val upper: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val avoidAmbiguous: Boolean = true,
)

object PasswordGenerator {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 64
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?"
    private const val AMBIGUOUS = "O0oIl1|"

    private fun pools(o: GeneratorOptions): List<String> =
        listOf(o.lower to LOWER, o.upper to UPPER, o.digits to DIGITS, o.symbols to SYMBOLS)
            .filter { it.first }.map { p -> if (o.avoidAmbiguous) p.second.filter { it !in AMBIGUOUS } else p.second }

    /** Cryptographically random password containing at least one character of every selected class. */
    fun generate(o: GeneratorOptions, rng: SecureRandom = SecureRandom()): String {
        val classes = pools(o).ifEmpty { listOf(LOWER) }
        val len = o.length.coerceIn(maxOf(MIN_LENGTH, classes.size), MAX_LENGTH)
        val all = classes.joinToString("")
        val chars = ArrayList<Char>(len)
        classes.forEach { chars += it[rng.nextInt(it.length)] }          // guarantee each class
        while (chars.size < len) chars += all[rng.nextInt(all.length)]
        for (i in chars.indices.reversed()) { val j = rng.nextInt(i + 1); val t = chars[i]; chars[i] = chars[j]; chars[j] = t } // Fisher-Yates
        return String(chars.toCharArray())
    }
}

enum class Strength { WEAK, FAIR, STRONG }

object PasswordStrength {
    private val COMMON = setOf("password", "123456", "12345678", "qwerty", "letmein", "admin", "welcome", "iloveyou", "abc123", "111111", "000000", "passw0rd", "monkey", "dragon")

    /** Rough entropy estimate (bits) from character classes and length, with penalties for repetition and common passwords. */
    fun bits(p: String): Double {
        if (p.isEmpty()) return 0.0
        if (p.lowercase() in COMMON) return 5.0
        var pool = 0
        if (p.any { it.isLowerCase() }) pool += 26
        if (p.any { it.isUpperCase() }) pool += 26
        if (p.any { it.isDigit() }) pool += 10
        if (p.any { !it.isLetterOrDigit() }) pool += 32
        if (pool == 0) pool = 26
        val distinct = p.toSet().size
        val effectiveLen = minOf(p.length.toDouble(), distinct * 2.0)  // "aaaaaaaaaa" is not 10 independent characters
        return effectiveLen * log2(pool.toDouble())
    }

    fun rate(p: String): Strength = when {
        bits(p) < 40 -> Strength.WEAK
        bits(p) < 64 -> Strength.FAIR
        else -> Strength.STRONG
    }
}

/** Parsing and writing of the common browser CSV password export (Chrome/Edge/Firefox/Brave column names). */
data class CsvLogin(val url: String, val username: String, val password: String, val note: String)
data class CsvParseResult(val logins: List<CsvLogin>, val skipped: Int, val truncated: Boolean)

object LoginCsv {
    const val MAX_BYTES = 2 * 1024 * 1024
    const val MAX_ROWS = 5000

    /** RFC-4180-style parser (quoted fields, doubled quotes, embedded newlines). */
    fun parseRows(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>(); var row = ArrayList<String>(); val sb = StringBuilder()
        var inQuotes = false; var i = 0; var any = false
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> if (c == '"') { if (i + 1 < text.length && text[i + 1] == '"') { sb.append('"'); i++ } else inQuotes = false } else sb.append(c)
                c == '"' -> { inQuotes = true; any = true }
                c == ',' -> { row.add(sb.toString()); sb.setLength(0); any = true }
                c == '\n' || c == '\r' -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    if (any || sb.isNotEmpty()) { row.add(sb.toString()); rows.add(row) }
                    row = ArrayList(); sb.setLength(0); any = false
                }
                else -> { sb.append(c); any = true }
            }
            i++
            if (rows.size > MAX_ROWS + 1) break
        }
        if (any || sb.isNotEmpty()) { row.add(sb.toString()); rows.add(row) }
        return rows
    }

    fun parse(text: String): CsvParseResult {
        val rows = parseRows(text.removePrefix("\uFEFF"))
        if (rows.isEmpty()) return CsvParseResult(emptyList(), 0, false)
        val header = rows[0].map { it.trim().lowercase() }
        fun col(vararg names: String) = header.indexOfFirst { it in names }
        val cUrl = col("url", "origin", "website", "login_uri", "hostname"); val cUser = col("username", "login", "login_username", "user")
        val cPass = col("password", "login_password"); val cNote = col("note", "notes", "comment")
        if (cUrl < 0 || cPass < 0) return CsvParseResult(emptyList(), rows.size - 1, false)
        val out = ArrayList<CsvLogin>(); var skipped = 0; var truncated = false
        for (r in rows.drop(1)) {
            if (out.size >= MAX_ROWS) { truncated = true; break }
            fun get(i: Int) = if (i in r.indices) r[i].trim() else ""
            val url = get(cUrl); val pw = if (cPass in r.indices) r[cPass] else ""
            if (!UrlTools.isWebUrl(url) || UrlTools.hasCredentials(url) || pw.isEmpty() || pw.length > 1000) { skipped++; continue }
            out += CsvLogin(url, get(cUser).take(300), pw, get(cNote).take(1000))
        }
        return CsvParseResult(out, skipped, truncated)
    }

    /** Values are written exactly as stored (never altered), so exported passwords stay correct. Don't open the file in a spreadsheet app. */
    private fun cell(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""

    fun write(items: List<CsvLogin>): String = buildString {
        append("name,url,username,password,note\n")
        items.forEach { append(cell(UrlTools.hostOf(it.url).orEmpty())).append(',').append(cell(it.url)).append(',').append(cell(it.username)).append(',').append(cell(it.password)).append(',').append(cell(it.note)).append('\n') }
    }
}

/** What a scanned QR/barcode string is, so the UI can describe it before anything is opened. */
sealed interface QrContent {
    val raw: String
    data class Web(override val raw: String, val host: String) : QrContent
    data class Wifi(override val raw: String, val ssid: String, val password: String, val security: String) : QrContent
    data class Contact(override val raw: String, val kind: String) : QrContent
    data class Text(override val raw: String) : QrContent
}

object QrClassifier {
    private fun unescape(s: String) = Regex("\\\\(.)").replace(s) { it.groupValues[1] }
    private fun field(body: String, key: String): String {
        val m = Regex("(?:^|;)$key:((?:\\\\.|[^;\\\\])*)").find(body) ?: return ""
        return unescape(m.groupValues[1])
    }

    fun classify(raw: String): QrContent {
        val t = raw.trim()
        if (UrlTools.isWebUrl(t) && !UrlTools.hasCredentials(t) && t.none { it.isISOControl() }) return QrContent.Web(t, UrlTools.hostOf(t).orEmpty())
        if (t.startsWith("WIFI:", ignoreCase = true)) {
            val body = t.substring(5)
            return QrContent.Wifi(t, field(body, "S"), field(body, "P"), field(body, "T").ifBlank { "nopass" })
        }
        val scheme = UrlTools.scheme(t)
        if (scheme in setOf("mailto", "tel", "sms", "smsto", "geo", "matmsg", "begin") || t.startsWith("BEGIN:VCARD", true)) return QrContent.Contact(t, scheme ?: "contact")
        return QrContent.Text(t)
    }
}
