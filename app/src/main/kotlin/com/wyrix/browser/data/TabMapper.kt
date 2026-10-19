package com.wyrix.browser.data

import com.wyrix.browser.core.UrlTools
import com.wyrix.browser.tabs.TabState
import com.wyrix.browser.tabs.Workspaces

/** Converts between persisted rows and live tab state, validating anything read from disk. */
object TabMapper {
    const val MAX_STATE_CHARS = 400_000
    const val MAX_TOTAL_STATE_CHARS = 4_000_000

    fun toEntity(tab: TabState, position: Int, sessionState: String?): TabEntity = TabEntity(
        id = tab.id, url = tab.url, title = tab.title.take(300), workspaceId = tab.workspaceId, groupId = tab.groupId,
        pinned = tab.pinned, desktopMode = tab.desktopMode, position = position, createdAt = tab.createdAt,
        lastAccessed = tab.lastAccessed, sessionState = sessionState,
    )

    data class Restored(val tabs: List<TabState>, val states: Map<String, String>)

    /** Drops malformed rows (blank/duplicate ids, non-web URLs), unknown groups, and oversized state blobs. */
    fun restore(rows: List<TabEntity>, validGroupIds: Set<String>, validWorkspaceIds: Set<String>): Restored {
        val seen = HashSet<String>()
        val tabs = ArrayList<TabState>()
        val states = HashMap<String, String>()
        var budget = MAX_TOTAL_STATE_CHARS
        for (r in rows.sortedBy { it.position }) {
            if (r.id.isBlank() || r.id.length > 64 || !seen.add(r.id)) continue
            if (r.url.isNotBlank() && !UrlTools.isWebUrl(r.url)) continue
            val ws = if (r.workspaceId in validWorkspaceIds) r.workspaceId else Workspaces.PERSONAL
            tabs += TabState(
                id = r.id, url = r.url, title = r.title.take(300), workspaceId = ws,
                groupId = r.groupId?.takeIf { it in validGroupIds }, pinned = r.pinned, desktopMode = r.desktopMode,
                showHome = r.url.isBlank(), createdAt = r.createdAt, lastAccessed = r.lastAccessed,
            )
            val s = r.sessionState
            if (s != null && r.url.isNotBlank() && s.length <= MAX_STATE_CHARS && s.length <= budget) {
                states[r.id] = s
                budget -= s.length
            }
        }
        return Restored(tabs, states)
    }
}
