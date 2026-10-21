package com.wyrix.browser

import com.wyrix.browser.tabs.*
import org.junit.Assert.*
import org.junit.Test

class TabsStateTest {
    private fun t(id: String, url: String = "https://$id.example", priv: Boolean = false, ws: String = Workspaces.PERSONAL, opener: String? = null, la: Long = 0) =
        TabState(id = id, url = url, isPrivate = priv, workspaceId = ws, showHome = false, openerId = opener, lastAccessed = la)
    private fun state(vararg tabs: TabState, sel: String? = tabs.lastOrNull()?.id) = TabsState(tabs.toList(), sel)

    @Test fun addSelectsAndRespectsLimit() {
        var s = TabsState()
        repeat(TabsState.MAX_TABS) { s = s.withTab(t("t$it")) }
        assertEquals(TabsState.MAX_TABS, s.tabs.size)
        assertEquals(s, s.withTab(t("overflow")))
    }
    @Test fun duplicateIdsIgnored() = assertEquals(1, TabsState().withTab(t("a")).withTab(t("a")).tabs.size)
    @Test fun childInsertedAfterOpener() {
        val s = state(t("a"), t("b")).withTab(t("c", opener = "a"))
        assertEquals(listOf("a", "c", "b"), s.tabs.map { it.id })
    }
    @Test fun closingSelectedPicksOpenerThenNeighbour() {
        val s = state(t("a"), t("b"), t("c", opener = "a"), sel = "c")
        assertEquals("a", s.withClosed("c").selectedId)
        val s2 = state(t("a"), t("b"), t("c"), sel = "b")
        assertTrue(s2.withClosed("b").selectedId in setOf("a", "c"))
    }
    @Test fun closingLastLeavesNoSelection() = assertNull(state(t("a")).withClosed("a").selectedId)
    @Test fun closingUnselectedKeepsSelection() = assertEquals("b", state(t("a"), t("b"), sel = "b").withClosed("a").selectedId)
    @Test fun closingLastPrivateFallsBackToMostRecentNormal() {
        val s = state(t("n1", la = 1), t("n2", la = 5), t("p", priv = true), sel = "p")
        assertEquals("n2", s.withClosed("p").selectedId)
    }
    @Test fun neighbourStaysInWorkspace() {
        val s = state(t("a", ws = "work"), t("b", ws = "study"), t("c", ws = "work"), sel = "c")
        assertEquals("a", s.withClosed("c").selectedId)
    }
    @Test fun reopenRestoresNormalTabsOnly() {
        var s = state(t("a"), t("b"), sel = "b").withClosed("b")
        assertEquals(1, s.recentlyClosed.size)
        s = s.withReopened("b2", 10)
        assertEquals("https://b.example", s.selected!!.url); assertEquals("b2", s.selectedId); assertTrue(s.recentlyClosed.isEmpty())
        val p = state(t("p", priv = true)).withClosed("p")
        assertTrue(p.recentlyClosed.isEmpty())
    }
    @Test fun reopenSpecificClosedTab() {
        var s = state(t("a"), t("b"), t("c"), sel = "c").withClosed("a").withClosed("b")
        assertEquals(2, s.recentlyClosed.size)
        s = s.withReopenedId("a", "a2", 9)
        assertEquals("https://a.example", s.selected!!.url); assertEquals("a2", s.selectedId)
        assertEquals(listOf("b"), s.recentlyClosed.map { it.id })
        assertEquals(s, s.withReopenedId("missing", "x", 1))
        assertTrue(s.withoutRecentlyClosed().recentlyClosed.isEmpty())
    }
    @Test fun blankTabsNotRemembered() = assertTrue(state(t("a", url = "")).withClosed("a").recentlyClosed.isEmpty())
    @Test fun recentlyClosedCapped() {
        var s = TabsState()
        repeat(TabsState.MAX_RECENT + 5) { s = s.withTab(t("x$it")).withClosed("x$it") }
        assertEquals(TabsState.MAX_RECENT, s.recentlyClosed.size)
    }
    @Test fun moveWithinScopeOnly() {
        val s = state(t("a"), t("w", ws = "work"), t("b"), sel = "a")
        assertEquals(listOf("b", "a"), s.withMoved("a", 1).inScope(false, Workspaces.PERSONAL).map { it.id })
        assertEquals("w", s.withMoved("a", 1).tab("w")!!.id)
        assertEquals(s.tabs, s.withMoved("a", -1).tabs)
    }
    @Test fun pinnedFirstAndStable() {
        val s = state(t("a"), t("b"), t("c")).withPinned("c", true).withPinned("b", true)
        assertEquals(listOf("b", "c", "a").toSet(), s.tabs.map { it.id }.toSet()); assertTrue(s.tabs[0].pinned && s.tabs[1].pinned && !s.tabs[2].pinned)
    }
    @Test fun groupAssignAndRemove() {
        val s = state(t("a"), t("b")).withGroup("a", "g").withGroup("b", "g")
        assertEquals(2, s.tabs.count { it.groupId == "g" })
        assertTrue(s.withoutGroup("g").tabs.all { it.groupId == null })
    }
    @Test fun duplicateCopiesUrlNotPinOrOpener() {
        val s = state(t("a", opener = null)).withPinned("a", true).withDuplicate("a", "a2", 5)
        val d = s.tab("a2")!!; assertEquals("https://a.example", d.url); assertFalse(d.pinned); assertEquals("a2", s.selectedId)
    }
    @Test fun withoutPrivateRemovesAllPrivateAndFixesSelection() {
        val s = state(t("n", la = 3), t("p1", priv = true), t("p2", priv = true), sel = "p2").withoutPrivate()
        assertEquals(listOf("n"), s.tabs.map { it.id }); assertEquals("n", s.selectedId)
    }
    @Test fun rapidCreateCloseDoesNotCorrupt() {
        var s = TabsState()
        repeat(500) { i -> s = s.withTab(t("r$i")); if (i % 2 == 0) s = s.withClosed("r$i") }
        assertTrue(s.tabs.size <= TabsState.MAX_TABS); assertTrue(s.selectedId == null || s.tab(s.selectedId!!) != null)
    }
    @Test fun inScopeFiltersPrivateAndWorkspace() {
        val s = state(t("a"), t("w", ws = "work"), t("p", priv = true))
        assertEquals(listOf("a"), s.inScope(false, Workspaces.PERSONAL).map { it.id })
        assertEquals(listOf("p"), s.inScope(true, Workspaces.PERSONAL).map { it.id })
    }
}
