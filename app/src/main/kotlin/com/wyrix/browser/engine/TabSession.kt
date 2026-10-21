package com.wyrix.browser.engine

import com.wyrix.browser.core.FilenameSanitizer
import com.wyrix.browser.core.LoadDecision
import com.wyrix.browser.core.LoadPolicy
import com.wyrix.browser.tabs.ErrorKind
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse
import android.net.Uri
import com.wyrix.browser.core.UrlTools

typealias Perm = GeckoSession.PermissionDelegate.ContentPermission

/**
 * One GeckoSession plus its delegates. All engine callbacks are translated into [TabEvent]s; no UI
 * state is mutated here, so browser chrome always reflects authoritative engine events.
 */
class TabSession(
    val id: String,
    val session: GeckoSession,
    val isPrivate: Boolean,
    private val emit: (TabEvent) -> Unit,
    private val hooks: Hooks,
) {
    /** Services the engine layer needs from the owning SessionManager. */
    interface Hooks {
        fun onPopup(openerId: String, uri: String): GeckoSession?
        fun requestAndroidPermissions(perms: Array<String>, result: (Boolean) -> Unit)
    }

    @Volatile private var latestState: GeckoSession.SessionState? = null

    init {
        session.navigationDelegate = navigationDelegate()
        session.progressDelegate = progressDelegate()
        session.contentDelegate = contentDelegate()
        session.permissionDelegate = permissionDelegate()
        session.promptDelegate = promptDelegate()
        session.contentBlockingDelegate = object : ContentBlocking.Delegate {
            override fun onContentBlocked(session: GeckoSession, event: ContentBlocking.BlockEvent) {
                emit(TabEvent.Blocked(id, runCatching { UrlTools.hostOf(event.uri) }.getOrNull()))
            }
        }
    }

    fun load(url: String) = session.loadUri(url)
    fun reload() = session.reload()
    fun reloadBypassingCache() = session.reload(GeckoSession.LOAD_FLAGS_BYPASS_CACHE)
    fun stop() = session.stop()
    fun goBack() = session.goBack()
    fun goForward() = session.goForward()
    fun exitFullScreen() = session.exitFullScreen()

    fun setDesktopMode(desktop: Boolean) {
        session.settings.setUserAgentMode(if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
        session.settings.setViewportMode(if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
    }

    fun setTrackingProtection(enabled: Boolean) {
        session.settings.setUseTrackingProtection(enabled)
    }

    /** Serialized history/scroll state for restoring this tab later; null if unavailable or oversized. */
    fun serializedState(maxChars: Int): String? =
        runCatching { latestState?.toString() }.getOrNull()?.takeIf { it.length <= maxChars }

    fun restore(serialized: String): Boolean = runCatching {
        val st = GeckoSession.SessionState.fromString(serialized) ?: return false
        session.restoreState(st)
        true
    }.getOrDefault(false)

    fun close() {
        runCatching { session.close() }
    }

    // ---- delegates ---------------------------------------------------------------------------

    private fun navigationDelegate() = object : GeckoSession.NavigationDelegate {
        override fun onLocationChange(
            session: GeckoSession, url: String?, perms: MutableList<Perm>, hasUserGesture: Boolean,
        ) {
            emit(TabEvent.Location(id, url.orEmpty(), perms.map { it.toInfo() }))
        }

        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) { emit(TabEvent.Nav(id, canGoBack, null)) }
        override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) { emit(TabEvent.Nav(id, null, canGoForward)) }

        override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? =
            when (LoadPolicy.decide(request.uri)) {
                LoadDecision.ALLOW -> GeckoResult.fromValue(AllowOrDeny.ALLOW)
                LoadDecision.DENY -> GeckoResult.fromValue(AllowOrDeny.DENY)
                LoadDecision.CONFIRM_EXTERNAL -> {
                    emit(TabEvent.Request(id, PromptRequest.External(id, request.uri) { /* handled by UI */ }))
                    GeckoResult.fromValue(AllowOrDeny.DENY)
                }
            }

        override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? =
            hooks.onPopup(id, uri)?.let { GeckoResult.fromValue(it) }

        override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? {
            val kind = when (error.category) {
                WebRequestError.ERROR_CATEGORY_NETWORK -> ErrorKind.NETWORK
                WebRequestError.ERROR_CATEGORY_SECURITY -> ErrorKind.SECURITY
                WebRequestError.ERROR_CATEGORY_CONTENT -> ErrorKind.CONTENT
                WebRequestError.ERROR_CATEGORY_URI -> ErrorKind.URI
                WebRequestError.ERROR_CATEGORY_PROXY -> ErrorKind.PROXY
                WebRequestError.ERROR_CATEGORY_SAFEBROWSING -> ErrorKind.UNSAFE
                else -> ErrorKind.UNKNOWN
            }
            emit(TabEvent.LoadError(id, uri.orEmpty(), kind, error.code))
            // We render our own error UI (no "accept the risk" bypass for TLS failures); load an empty page underneath.
            return GeckoResult.fromValue(BLANK_PAGE)
        }
    }

    private fun progressDelegate() = object : GeckoSession.ProgressDelegate {
        override fun onPageStart(session: GeckoSession, url: String) { emit(TabEvent.PageStart(id, url)) }
        override fun onPageStop(session: GeckoSession, success: Boolean) { emit(TabEvent.PageStop(id, success)) }
        override fun onProgressChange(session: GeckoSession, progress: Int) { emit(TabEvent.Progress(id, progress)) }
        override fun onSecurityChange(session: GeckoSession, securityInfo: GeckoSession.ProgressDelegate.SecurityInformation) {
            emit(TabEvent.Security(id, securityInfo.isSecure))
        }
        override fun onSessionStateChange(session: GeckoSession, sessionState: GeckoSession.SessionState) {
            latestState = sessionState // serialized lazily; this callback can fire often
        }
    }

    private fun contentDelegate() = object : GeckoSession.ContentDelegate {
        override fun onTitleChange(session: GeckoSession, title: String?) { emit(TabEvent.Title(id, title.orEmpty())) }
        override fun onCloseRequest(session: GeckoSession) { emit(TabEvent.CloseRequest(id)) }
        override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) { emit(TabEvent.FullScreen(id, fullScreen)) }
        override fun onCrash(session: GeckoSession) { emit(TabEvent.Crashed(id)) }
        override fun onKill(session: GeckoSession) { emit(TabEvent.Crashed(id)) }

        override fun onContextMenu(session: GeckoSession, screenX: Int, screenY: Int, element: GeckoSession.ContentDelegate.ContextElement) {
            val link = element.linkUri?.takeIf { UrlTools.isWebUrl(it) }
            val image = element.srcUri?.takeIf {
                element.type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE && UrlTools.isWebUrl(it)
            }
            if (link != null || image != null) emit(TabEvent.ContextMenu(id, link, image))
        }

        override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
            val headers = response.headers
            fun header(name: String) = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
            emit(
                TabEvent.Download(
                    tabId = id,
                    uri = response.uri,
                    fileNameHint = FilenameSanitizer.fromContentDisposition(header("content-disposition")),
                    mime = header("content-type"),
                    contentLength = header("content-length")?.toLongOrNull() ?: -1L,
                    body = response.body,
                ),
            )
        }
    }

    private fun permissionDelegate() = object : GeckoSession.PermissionDelegate {
        override fun onContentPermissionRequest(session: GeckoSession, perm: Perm): GeckoResult<Int>? {
            val what = permissionLabel(perm.permission)
            // Notifications aren't supported (no push/notification delegate): deny explicitly rather than pretend.
            if (what == null || perm.permission == GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION) {
                return GeckoResult.fromValue(Perm.VALUE_DENY)
            }
            val result = GeckoResult<Int>()
            emit(TabEvent.Request(id, PromptRequest.Permission(id, originOf(perm.uri), what) { allow ->
                result.complete(if (allow) Perm.VALUE_ALLOW else Perm.VALUE_DENY)
            }))
            return result
        }

        override fun onAndroidPermissionsRequest(session: GeckoSession, permissions: Array<out String>?, callback: GeckoSession.PermissionDelegate.Callback) {
            if (permissions == null || permissions.isEmpty()) { callback.reject(); return }
            hooks.requestAndroidPermissions(arrayOf(*permissions)) { granted -> if (granted) callback.grant() else callback.reject() }
        }

        override fun onMediaPermissionRequest(
            session: GeckoSession, uri: String,
            video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
            audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
            callback: GeckoSession.PermissionDelegate.MediaCallback,
        ) {
            val v = video?.firstOrNull(); val a = audio?.firstOrNull()
            if (v == null && a == null) { callback.reject(); return }
            val what = listOfNotNull(if (v != null) "camera" else null, if (a != null) "microphone" else null).joinToString(" and ")
            emit(TabEvent.Request(id, PromptRequest.Permission(id, originOf(uri), "use your $what") { allow ->
                if (allow) callback.grant(v, a) else callback.reject()
            }))
        }
    }

    private fun promptDelegate() = object : GeckoSession.PromptDelegate {
        override fun onAlertPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.AlertPrompt): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
            val r = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            emit(TabEvent.Request(id, PromptRequest.Alert(id, prompt.title.orEmpty(), prompt.message.orEmpty()) { r.complete(prompt.dismiss()) }))
            return r
        }

        override fun onButtonPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.ButtonPrompt): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
            val r = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            emit(TabEvent.Request(id, PromptRequest.Confirm(id, prompt.title.orEmpty(), prompt.message.orEmpty()) { ok ->
                r.complete(
                    if (ok) prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE) else prompt.dismiss(),
                )
            }))
            return r
        }

        override fun onFilePrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.FilePrompt): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
            val r = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            val multiple = prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE
            val mimes = prompt.mimeTypes?.toList().orEmpty()
            emit(TabEvent.Request(id, PromptRequest.FilePick(id, multiple, mimes) { uris ->
                val ctx = appContext
                when {
                    uris.isNullOrEmpty() || ctx == null -> r.complete(prompt.dismiss())
                    multiple -> r.complete(prompt.confirm(ctx, uris.toTypedArray()))
                    else -> r.complete(prompt.confirm(ctx, uris.first()))
                }
            }))
            return r
        }
    }

    private fun Perm.toInfo() = SitePermissionInfo(permissionLabel(permission) ?: "Other", stateLabel(value), this)

    companion object {
        /** Empty page loaded underneath our native error overlay. */
        const val BLANK_PAGE = "data:text/html;charset=utf-8,%3C!doctype%20html%3E%3Cmeta%20name%3Dcolor-scheme%20content%3D%22dark%20light%22%3E"
        @Volatile var appContext: android.content.Context? = null

        private fun originOf(uri: String): String = UrlTools.hostOf(uri) ?: uri.take(80)

        fun permissionLabel(type: Int): String? = when (type) {
            GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> "Location"
            GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> "Notifications"
            GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> "Persistent storage"
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE -> "Autoplay (with sound)"
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE -> "Autoplay (muted)"
            GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> "Protected media (DRM)"
            else -> null
        }

        private fun stateLabel(value: Int): String = when (value) {
            Perm.VALUE_ALLOW -> "Allowed"
            Perm.VALUE_DENY -> "Blocked"
            else -> "Ask"
        }

        fun newSettings(isPrivate: Boolean, desktop: Boolean, trackingProtection: Boolean): GeckoSessionSettings =
            GeckoSessionSettings.Builder()
                .usePrivateMode(isPrivate)
                .useTrackingProtection(trackingProtection)
                .userAgentMode(if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                .viewportMode(if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
                .build()
    }
}
