package com.wyrix.browser.core

import android.util.Log
import com.wyrix.browser.BuildConfig

/** Logging helper: never logs full URLs; never logs anything about private tabs. */
object Redactor {
    fun describe(url: String?, isPrivate: Boolean): String =
        if (isPrivate) "<private>" else url?.let { UrlTools.hostOf(it) } ?: "<none>"

    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d("Wyrix", message)
    }
}
