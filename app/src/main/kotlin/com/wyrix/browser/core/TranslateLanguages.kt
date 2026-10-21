package com.wyrix.browser.core

import java.net.URLEncoder

/**
 * Page translation via Google Translate's web proxy. This is NOT on-device translation: the page address is sent to
 * Google. The UI states that and asks for confirmation every time.
 */
object TranslateLanguages {
    val all: List<Pair<String, String>> = listOf(
        "en" to "English", "es" to "Español", "fr" to "Français", "de" to "Deutsch", "it" to "Italiano", "pt" to "Português",
        "nl" to "Nederlands", "pl" to "Polski", "ru" to "Русский", "tr" to "Türkçe", "ar" to "العربية", "hi" to "हिन्दी",
        "bn" to "বাংলা", "ur" to "اردو", "id" to "Bahasa Indonesia", "vi" to "Tiếng Việt", "th" to "ไทย", "ja" to "日本語",
        "ko" to "한국어", "zh-CN" to "中文 (简体)",
    )

    fun isSupported(code: String) = all.any { it.first == code }

    fun defaultFor(deviceLanguage: String): String = all.firstOrNull { it.first.equals(deviceLanguage, ignoreCase = true) }?.first ?: "en"

    fun buildUrl(pageUrl: String, target: String): String? {
        if (!isSupported(target) || !UrlTools.isWebUrl(pageUrl) || UrlTools.hasCredentials(pageUrl)) return null
        return "https://translate.google.com/translate?sl=auto&tl=" + URLEncoder.encode(target, "UTF-8") + "&u=" + URLEncoder.encode(pageUrl, "UTF-8")
    }
}
