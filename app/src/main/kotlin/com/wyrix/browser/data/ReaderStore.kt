package com.wyrix.browser.data

import android.content.Context
import com.wyrix.browser.core.ReaderArticle
import com.wyrix.browser.core.ReaderBlock
import com.wyrix.browser.core.UrlTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class SavedArticleMeta(val id: String, val title: String, val siteName: String, val url: String, val savedAt: Long)

/** Offline reading list: one JSON file per article in app-private storage (excluded from backups). */
class ReaderStore(context: Context) {
    private val dir = File(context.filesDir, "reader").apply { mkdirs() }

    fun idFor(url: String): String =
        MessageDigest.getInstance("SHA-256").digest(UrlTools.normalize(url).toByteArray()).take(12).joinToString("") { "%02x".format(it) }

    suspend fun isSaved(url: String): Boolean = withContext(Dispatchers.IO) { File(dir, idFor(url) + ".json").exists() }

    suspend fun save(a: ReaderArticle, now: Long = System.currentTimeMillis()): Boolean = withContext(Dispatchers.IO) {
        if ((dir.listFiles()?.size ?: 0) >= MAX_ARTICLES && !File(dir, idFor(a.url) + ".json").exists()) return@withContext false
        val arr = JSONArray()
        a.blocks.forEach { b ->
            val t = when (b) { is ReaderBlock.Heading -> "h${b.level}"; is ReaderBlock.Paragraph -> "p"; is ReaderBlock.Quote -> "q"; is ReaderBlock.Item -> "li"; is ReaderBlock.Code -> "c" }
            arr.put(JSONObject().put("t", t).put("x", b.text))
        }
        val o = JSONObject().put("url", a.url).put("title", a.title).put("site", a.siteName).put("savedAt", now).put("blocks", arr)
        File(dir, idFor(a.url) + ".json").writeText(o.toString())
        true
    }

    suspend fun list(): List<SavedArticleMeta> = withContext(Dispatchers.IO) {
        (dir.listFiles() ?: emptyArray()).mapNotNull { f ->
            runCatching {
                val o = JSONObject(f.readText())
                SavedArticleMeta(f.nameWithoutExtension, o.optString("title"), o.optString("site"), o.optString("url"), o.optLong("savedAt"))
            }.getOrNull()
        }.sortedByDescending { it.savedAt }
    }

    suspend fun load(id: String): ReaderArticle? = withContext(Dispatchers.IO) {
        if (!id.all { it.isLetterOrDigit() }) return@withContext null // id comes from our own list, but never build paths from unchecked text
        runCatching {
            val o = JSONObject(File(dir, "$id.json").readText())
            val blocks = (0 until o.getJSONArray("blocks").length()).mapNotNull { i ->
                val b = o.getJSONArray("blocks").getJSONObject(i); val x = b.optString("x")
                when (val t = b.optString("t")) {
                    "p" -> ReaderBlock.Paragraph(x); "q" -> ReaderBlock.Quote(x); "li" -> ReaderBlock.Item(x); "c" -> ReaderBlock.Code(x)
                    else -> if (t.length == 2 && t[0] == 'h') ReaderBlock.Heading((t[1] - '0').coerceIn(1, 4), x) else null
                }
            }
            ReaderArticle(o.getString("url"), o.optString("title"), o.optString("site"), blocks)
        }.getOrNull()
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) { if (id.all { it.isLetterOrDigit() }) File(dir, "$id.json").delete() }

    companion object { const val MAX_ARTICLES = 200 }
}

/**
 * Downloads a page for Reader mode. HTTPS only, no cookies, bounded size/time/redirects. This is a second request made by
 * the app (not the engine), which is why reader mode only works for public, static pages.
 */
object ReaderFetcher {
    private const val MAX_BYTES = 1_500_000
    private const val MAX_REDIRECTS = 4

    sealed interface Result {
        data class Ok(val html: String) : Result
        data class Failed(val message: String) : Result
    }

    fun fetch(startUrl: String): Result {
        var url = startUrl
        repeat(MAX_REDIRECTS + 1) {
            if (!url.startsWith("https://") || !UrlTools.isWebUrl(url)) return Result.Failed("Reader mode needs a secure (HTTPS) page.")
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 8000; c.readTimeout = 10000; c.instanceFollowRedirects = false
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) WYRIX-Reader")
                c.setRequestProperty("Accept", "text/html,application/xhtml+xml")
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: return Result.Failed("The site redirected somewhere unusable.")
                    url = URL(URL(url), loc).toString(); return@repeat
                }
                if (code != 200) return Result.Failed("The site answered with error $code.")
                val type = c.contentType.orEmpty().lowercase()
                if (!type.contains("html")) return Result.Failed("This isn't an HTML article.")
                val cs = Regex("charset=([\\w-]+)").find(type)?.groupValues?.get(1)?.let { runCatching { java.nio.charset.Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
                val buf = ByteArray(MAX_BYTES); var n = 0
                c.inputStream.use { s -> while (n < MAX_BYTES) { val r = s.read(buf, n, MAX_BYTES - n); if (r < 0) break; n += r } }
                return Result.Ok(String(buf, 0, n, cs))
            } catch (e: Exception) {
                return Result.Failed("Couldn't load the page for reading (${e.javaClass.simpleName}).")
            } finally { c.disconnect() }
        }
        return Result.Failed("Too many redirects.")
    }
}
