package com.wyrix.browser.engine

import android.os.Handler
import android.os.Looper
import com.wyrix.browser.core.PopupRateLimiter
import com.wyrix.browser.data.TabMapper
import com.wyrix.browser.tabs.TabState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.mozilla.geckoview.GeckoSession
import java.util.UUID

/**
 * Owns live GeckoSessions. Keeps at most [MAX_LOADED] loaded; least-recently-used background tabs
 * are suspended (state saved, session closed) and transparently restored when selected again.
 */
class SessionManager(private val engine: EngineHolder) : TabSession.Hooks {
    private val sessions = LinkedHashMap<String, TabSession>(16, 0.75f, true)
    private val saved = HashMap<String, String>()
    private val limiter = PopupRateLimiter()
    private val main = Handler(Looper.getMainLooper())

    private val _events = MutableSharedFlow<TabEvent>(extraBufferCapacity = 512)
    val events: SharedFlow<TabEvent> = _events.asSharedFlow()

    /** Set by the UI; asks the user for Android runtime permissions. */
    var androidPermissionRequester: ((Array<String>, (Boolean) -> Unit) -> Unit)? = null
    /** Provided by the ViewModel: protection on? popups allowed for opener? */
    var trackingProtectionOn: () -> Boolean = { true }
    var limitPopups: () -> Boolean = { true }
    var isTabSelected: (String) -> Boolean = { true }

    private fun emit(e: TabEvent) { _events.tryEmit(e) }

    fun peek(id: String): TabSession? = sessions[id]
    fun seedStates(states: Map<String, String>) { saved.putAll(states) }

    /** Returns the live session for [tab], creating or restoring it if needed. */
    fun ensure(tab: TabState): TabSession {
        sessions[tab.id]?.let { return it }
        val ts = create(tab.id, tab.isPrivate, tab.desktopMode, !tab.trackingOff && trackingProtectionOn())
        val state = saved.remove(tab.id)
        if (!(state != null && ts.restore(state)) && tab.url.isNotBlank()) ts.load(tab.url)
        evictIfNeeded(tab.id)
        return ts
    }

    /** Navigates [tab] to [url], creating the session on first use. */
    fun navigate(tab: TabState, url: String): TabSession {
        saved.remove(tab.id)
        val existing = sessions[tab.id]
        if (existing != null) { existing.load(url); return existing }
        val ts = create(tab.id, tab.isPrivate, tab.desktopMode, !tab.trackingOff && trackingProtectionOn())
        ts.load(url)
        evictIfNeeded(tab.id)
        return ts
    }

    private fun create(id: String, isPrivate: Boolean, desktop: Boolean, tracking: Boolean): TabSession {
        val gs = GeckoSession(TabSession.newSettings(isPrivate, desktop, tracking))
        val ts = TabSession(id, gs, isPrivate, ::emit, this)
        gs.open(engine.runtime)
        sessions[id] = ts
        return ts
    }

    private fun evictIfNeeded(keep: String) {
        while (sessions.size > MAX_LOADED) {
            val victim = sessions.keys.firstOrNull { it != keep && !isTabSelected(it) } ?: return
            suspend(victim)
        }
    }

    /** Frees the engine session but remembers state so the tab can be restored. */
    fun suspend(id: String) {
        val ts = sessions.remove(id) ?: return
        ts.serializedState(TabMapper.MAX_STATE_CHARS)?.let { saved[id] = it }
        closeLater(ts)
    }

    fun remove(id: String) {
        saved.remove(id)
        sessions.remove(id)?.let { closeLater(it) }
    }

    /** Drops a crashed session so the next [ensure] builds a fresh one. */
    fun forget(id: String) {
        sessions.remove(id)?.let { closeLater(it) }
    }

    /** Closing right away while a GeckoView may still display it is unsafe; give the UI a frame to detach. */
    private fun closeLater(ts: TabSession) { main.postDelayed({ ts.close() }, 200) }

    fun trim(selectedId: String?, keep: Int) {
        val victims = sessions.keys.filter { it != selectedId }.dropLast(maxOf(0, keep - 1))
        victims.forEach { suspend(it) }
    }

    fun statesFor(ids: Collection<String>): Map<String, String> {
        val out = HashMap<String, String>()
        for (id in ids) {
            val s = sessions[id]?.serializedState(TabMapper.MAX_STATE_CHARS) ?: saved[id]
            if (s != null) out[id] = s
        }
        return out
    }

    fun isLoaded(id: String) = sessions.containsKey(id)

    // ---- TabSession.Hooks ---------------------------------------------------------------------
    override fun onPopup(openerId: String, uri: String): GeckoSession? {
        val opener = sessions[openerId] ?: return null
        if (limitPopups() && (!isTabSelected(openerId) || !limiter.tryAcquire())) return null
        val newId = UUID.randomUUID().toString()
        val gs = GeckoSession(TabSession.newSettings(opener.isPrivate, false, trackingProtectionOn()))
        // Gecko opens the returned session itself; the UI opens it defensively if it attaches first.
        sessions[newId] = TabSession(newId, gs, opener.isPrivate, ::emit, this)
        emit(TabEvent.PopupOpened(newId, openerId, opener.isPrivate, uri))
        return gs
    }

    override fun requestAndroidPermissions(perms: Array<String>, result: (Boolean) -> Unit) {
        val r = androidPermissionRequester
        if (r == null) result(false) else r(perms, result)
    }

    companion object { const val MAX_LOADED = 8 }
}
