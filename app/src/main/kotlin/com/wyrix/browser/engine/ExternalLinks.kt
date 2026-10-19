package com.wyrix.browser.engine

import android.content.Intent
import android.net.Uri
import com.wyrix.browser.core.UrlTools

/** Builds a sanitised intent for a user-approved hand-off to another app. */
object ExternalLinks {
    data class Prepared(val intent: Intent, val fallbackUrl: String?, val description: String)

    private val ALLOWED_ACTIONS = setOf(Intent.ACTION_VIEW, Intent.ACTION_SEND, Intent.ACTION_SENDTO, Intent.ACTION_DIAL)

    fun prepare(uri: String): Prepared? {
        return when (UrlTools.scheme(uri)) {
            "intent" -> runCatching {
                val i = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
                if (i.action !in ALLOWED_ACTIONS) return null
                val fb = i.getStringExtra("browser_fallback_url")?.takeIf { UrlTools.isWebUrl(it) }
                i.component = null   // never target a specific component
                i.selector = null
                i.flags = 0
                i.addCategory(Intent.CATEGORY_BROWSABLE)
                Prepared(i, fb, "another app")
            }.getOrNull()
            "tel" -> Prepared(Intent(Intent.ACTION_DIAL, Uri.parse(uri)), null, "the phone app")
            "mailto" -> Prepared(Intent(Intent.ACTION_SENDTO, Uri.parse(uri)), null, "your email app")
            "sms", "smsto" -> Prepared(Intent(Intent.ACTION_SENDTO, Uri.parse(uri)), null, "your messaging app")
            "market", "geo" -> Prepared(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addCategory(Intent.CATEGORY_BROWSABLE), null, "another app")
            else -> null
        }
    }
}
