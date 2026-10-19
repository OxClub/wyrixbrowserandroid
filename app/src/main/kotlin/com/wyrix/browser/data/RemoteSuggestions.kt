package com.wyrix.browser.data

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Optional network autocomplete. Off by default; never used in private tabs. Sends the partially typed
 * text to the selected search engine, which is why the UI labels these results and offers a switch.
 */
object RemoteSuggestions {
    private const val MAX_BYTES = 64 * 1024

    fun fetch(suggestTemplate: String, query: String): List<String> {
        if (query.isBlank() || query.length > 200) return emptyList()
        val url = suggestTemplate.replace("%s", URLEncoder.encode(query, "UTF-8"))
        if (!url.startsWith("https://")) return emptyList()
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 2000; c.readTimeout = 2000; c.instanceFollowRedirects = false
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode != 200) return emptyList()
            val bytes = c.inputStream.use { stream ->
                val buf = ByteArray(MAX_BYTES)
                var n = 0
                while (n < MAX_BYTES) {
                    val r = stream.read(buf, n, MAX_BYTES - n)
                    if (r < 0) break
                    n += r
                }
                buf.copyOf(n)
            }
            val arr = JSONArray(String(bytes, Charsets.UTF_8))
            (0 until minOf(arr.length(), 6)).mapNotNull { arr.optJSONObject(it)?.optString("phrase")?.takeIf { s -> s.isNotBlank() } }
        } catch (e: Exception) {
            emptyList()
        } finally {
            c.disconnect()
        }
    }
}
