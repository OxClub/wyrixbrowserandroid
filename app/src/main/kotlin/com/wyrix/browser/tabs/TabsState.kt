package com.wyrix.browser.tabs

import kotlin.math.abs

enum class ErrorKind { NETWORK, SECURITY, CONTENT, URI, PROXY, UNSAFE, UNKNOWN }

data class TabError(val url: String, val kind: ErrorKind, val code: Int, val httpFallback: String?)

object Workspaces {
    const val PERSONAL = "personal"
    const val WORK = "work"
    const val STUDY = "study"
}

/** Immutable per-tab UI state. The engine session is held separately by SessionManager. */
data class TabState(
    val id: String,
    val url: String = "",
    val title: String = "",
    val isPrivate: Boolean = false,
    val workspaceId: String = Workspaces.PERSONAL,
    val groupId: String? = null,
    val pinned: Boolean = false,
    /** True while the local home dashboard is drawn over (or instead of) the page. */
    val showHome: Boolean = true,
    val loading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val secure: Boolean? = null,
    val desktopMode: Boolean = false,
    val trackingOff: Boolean = false,
    val openerId: String? = null,
    val crashed: Boolean = false,
    val error: TabError? = null,
    val httpFallback: String? = null,
    val blocked: Int = 0,
    val createdAt: Long = 0,
    val lastAccessed: Long = 0,
) {
    val hasContent: Boolean get() = url.isNotBlank()
}

/**
 * Pure tab-list reducer. Every function returns a new state; nothing here touches Android or
 * the engine, so selection/closing/reopening rules are unit-tested.
 */
data class TabsState(
    val tabs: List<TabState> = emptyList(),
    val selectedId: String? = null,
    val recentlyClosed: List<TabState> = emptyList(),
) {
    val selected: TabState? get() = tabs.firstOrNull { it.id == selectedId }
    fun tab(id: String): TabState? = tabs.firstOrNull { it.id == id }

    fun inScope(isPrivate: Boolean, workspaceId: String?): List<TabState> =
        tabs.filter { it.isPrivate == isPrivate && (isPrivate || workspaceId == null || it.workspaceId == workspaceId) }

    fun canAdd(): Boolean = tabs.size < MAX_TABS

    fun withTab(tab: TabState, select: Boolean = true): TabsState {
        if (!canAdd() || tabs.any { it.id == tab.id }) return this
        val openerIdx = tab.openerId?.let { id -> tabs.indexOfFirst { it.id == id } } ?: -1
        val newTabs = if (openerIdx >= 0) tabs.toMutableList().apply { add(openerIdx + 1, tab) } else tabs + tab
        return copy(tabs = newTabs, selectedId = if (select) tab.id else selectedId)
    }

    fun withSelected(id: String, now: Long): TabsState {
        if (tab(id) == null) return this
        return copy(selectedId = id, tabs = tabs.map { if (it.id == id) it.copy(lastAccessed = now) else it })
    }

    fun withUpdate(id: String, f: (TabState) -> TabState): TabsState {
        if (tab(id) == null) return this
        return copy(tabs = tabs.map { if (it.id == id) f(it) else it })
    }

    fun withClosed(id: String): TabsState {
        val idx = tabs.indexOfFirst { it.id == id }
        if (idx < 0) return this
        val closed = tabs[idx]
        val remaining = tabs.filterIndexed { i, _ -> i != idx }
        val recent = if (closed.isPrivate || !closed.hasContent) recentlyClosed
        else (listOf(closed.copy(loading = false, progress = 0, error = null, crashed = false, openerId = null)) + recentlyClosed).take(MAX_RECENT)
        val newSel = if (selectedId != id) selectedId else pickNext(remaining, closed, idx)
        return copy(tabs = remaining, selectedId = newSel, recentlyClosed = recent)
    }

    private fun pickNext(remaining: List<TabState>, closed: TabState, closedIdx: Int): String? {
        closed.openerId?.let { op -> remaining.firstOrNull { it.id == op }?.let { return it.id } }
        val scope = remaining.filter {
            it.isPrivate == closed.isPrivate && (closed.isPrivate || it.workspaceId == closed.workspaceId)
        }
        if (scope.isNotEmpty()) {
            return scope.minByOrNull { t -> abs(tabs.indexOfFirst { it.id == t.id } - closedIdx) }?.id
        }
        // Closed the last private tab: fall back to the most recently used normal tab.
        return if (closed.isPrivate) remaining.filter { !it.isPrivate }.maxByOrNull { it.lastAccessed }?.id else null
    }

    fun withReopened(newId: String, now: Long): TabsState {
        val t = recentlyClosed.firstOrNull() ?: return this
        if (!canAdd()) return this
        val reopened = t.copy(id = newId, showHome = false, lastAccessed = now, createdAt = now)
        return copy(recentlyClosed = recentlyClosed.drop(1)).withTab(reopened, select = true)
    }

    fun withDuplicate(id: String, newId: String, now: Long): TabsState {
        val t = tab(id) ?: return this
        if (!canAdd()) return this
        val dup = t.copy(
            id = newId, pinned = false, loading = false, progress = 0, error = null, crashed = false,
            canGoBack = false, canGoForward = false, openerId = null, createdAt = now, lastAccessed = now, blocked = 0,
        )
        val idx = tabs.indexOfFirst { it.id == id }
        return copy(tabs = tabs.toMutableList().apply { add(idx + 1, dup) }, selectedId = newId)
    }

    /** Moves a tab [delta] positions among tabs of the same scope (private flag + workspace). */
    fun withMoved(id: String, delta: Int): TabsState {
        val t = tab(id) ?: return this
        val scopeIdx = tabs.indices.filter { i ->
            tabs[i].isPrivate == t.isPrivate && (t.isPrivate || tabs[i].workspaceId == t.workspaceId)
        }
        val pos = scopeIdx.indexOfFirst { tabs[it].id == id }
        val target = (pos + delta).coerceIn(0, scopeIdx.size - 1)
        if (target == pos) return this
        val list = tabs.toMutableList()
        val a = scopeIdx[pos]; val b = scopeIdx[target]
        val item = list.removeAt(a)
        list.add(b, item)
        return copy(tabs = list)
    }

    fun withPinned(id: String, pinned: Boolean): TabsState {
        if (tab(id) == null) return this
        val updated = tabs.map { if (it.id == id) it.copy(pinned = pinned) else it }
        // Stable partition: pinned tabs first within the list.
        return copy(tabs = updated.filter { it.pinned } + updated.filterNot { it.pinned })
    }

    fun withGroup(id: String, groupId: String?): TabsState = withUpdate(id) { it.copy(groupId = groupId) }
    fun withoutGroup(groupId: String): TabsState =
        copy(tabs = tabs.map { if (it.groupId == groupId) it.copy(groupId = null) else it })
    fun withoutPrivate(): TabsState {
        val remaining = tabs.filterNot { it.isPrivate }
        val sel = if (selected?.isPrivate == true) remaining.maxByOrNull { it.lastAccessed }?.id else selectedId
        return copy(tabs = remaining, selectedId = sel)
    }

    companion object {
        const val MAX_TABS = 100
        const val MAX_RECENT = 20
    }
}
