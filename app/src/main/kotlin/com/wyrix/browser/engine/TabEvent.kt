package com.wyrix.browser.engine

import android.net.Uri
import com.wyrix.browser.tabs.ErrorKind
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission
import java.io.InputStream

/** A site permission as reported by the engine for the current page. */
data class SitePermissionInfo(val kind: String, val state: String, val raw: ContentPermission)

/** Requests that need a user decision. The completion lambdas resolve the engine's pending result. */
sealed interface PromptRequest {
    val tabId: String
    /** Called if the tab goes away before the user decides, so engine callbacks never leak. */
    fun cancel()

    data class Alert(override val tabId: String, val title: String, val message: String, val done: () -> Unit) : PromptRequest {
        override fun cancel() = done()
    }
    data class Confirm(override val tabId: String, val title: String, val message: String, val result: (Boolean) -> Unit) : PromptRequest {
        override fun cancel() = result(false)
    }
    data class FilePick(override val tabId: String, val multiple: Boolean, val mimeTypes: List<String>, val result: (List<Uri>?) -> Unit) : PromptRequest {
        override fun cancel() = result(null)
    }
    data class Permission(override val tabId: String, val origin: String, val what: String, val result: (Boolean) -> Unit) : PromptRequest {
        override fun cancel() = result(false)
    }
    data class External(override val tabId: String, val uri: String, val result: (Boolean) -> Unit) : PromptRequest {
        override fun cancel() = result(false)
    }
}

data class ContextMenuInfo(val tabId: String, val linkUri: String?, val imageUri: String?, val isPrivate: Boolean)

sealed interface TabEvent {
    val tabId: String
    data class Location(override val tabId: String, val url: String, val permissions: List<SitePermissionInfo>) : TabEvent
    data class Title(override val tabId: String, val title: String) : TabEvent
    data class PageStart(override val tabId: String, val url: String) : TabEvent
    data class PageStop(override val tabId: String, val success: Boolean) : TabEvent
    data class Progress(override val tabId: String, val progress: Int) : TabEvent
    data class Nav(override val tabId: String, val canGoBack: Boolean?, val canGoForward: Boolean?) : TabEvent
    data class Security(override val tabId: String, val secure: Boolean) : TabEvent
    data class LoadError(override val tabId: String, val url: String, val kind: ErrorKind, val code: Int) : TabEvent
    data class CloseRequest(override val tabId: String) : TabEvent
    data class FullScreen(override val tabId: String, val on: Boolean) : TabEvent
    data class Crashed(override val tabId: String) : TabEvent
    data class Blocked(override val tabId: String) : TabEvent
    data class PopupOpened(override val tabId: String, val openerId: String, val isPrivate: Boolean, val uri: String) : TabEvent
    data class ContextMenu(override val tabId: String, val linkUri: String?, val imageUri: String?) : TabEvent
    data class Download(
        override val tabId: String, val uri: String, val fileNameHint: String?, val mime: String?,
        val contentLength: Long, val body: InputStream?,
    ) : TabEvent
    data class Request(override val tabId: String, val request: PromptRequest) : TabEvent
}
