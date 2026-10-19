package com.wyrix.browser.core

data class SearchEngine(
    val id: String,
    val name: String,
    /** URL template; `%s` is replaced with the percent-encoded query. */
    val template: String,
    /** Autocomplete endpoint (JSON `[{"phrase": ...}]`). Only engines verified to use this shape set it. */
    val suggestUrl: String? = null,
)

object SearchEngines {
    const val CUSTOM_ID = "custom"
    const val DEFAULT_ID = "duckduckgo"

    val all: List<SearchEngine> = listOf(
        SearchEngine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s", "https://duckduckgo.com/ac/?q=%s"),
        SearchEngine("brave", "Brave Search", "https://search.brave.com/search?q=%s"),
        SearchEngine("startpage", "Startpage", "https://www.startpage.com/do/search?q=%s"),
        SearchEngine("ecosia", "Ecosia", "https://www.ecosia.org/search?q=%s"),
        SearchEngine("google", "Google", "https://www.google.com/search?q=%s"),
        SearchEngine("bing", "Bing", "https://www.bing.com/search?q=%s"),
    )

    fun byId(id: String): SearchEngine? = all.firstOrNull { it.id == id }

    /** A custom template must be HTTPS, contain exactly one `%s`, and have a host. */
    fun isValidTemplate(t: String): Boolean =
        t.length <= 512 && t.startsWith("https://") && Regex("%s").findAll(t).count() == 1 &&
            UrlTools.hostOf(t.replace("%s", "q")) != null

    fun templateFor(id: String, custom: String): String =
        if (id == CUSTOM_ID && isValidTemplate(custom)) custom
        else (byId(id) ?: all.first()).template
}
