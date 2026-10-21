package com.wyrix.browser

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.wyrix.browser.core.PopupRateLimiter
import com.wyrix.browser.core.SearchEngines
import com.wyrix.browser.core.groupByDay
import com.wyrix.browser.data.*
import com.wyrix.browser.tabs.Workspaces
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class PopupRateLimiterTest {
    @Test fun limitsWithinWindowAndRecovers() {
        var now = 0L
        val l = PopupRateLimiter(max = 3, windowMs = 1000, clock = { now })
        assertTrue(l.tryAcquire()); assertTrue(l.tryAcquire()); assertTrue(l.tryAcquire())
        assertFalse(l.tryAcquire())
        now = 1001
        assertTrue(l.tryAcquire())
    }
}

class SearchEnginesTest {
    @Test fun builtInTemplatesAreHttpsWithOnePlaceholder() = SearchEngines.all.forEach { assertTrue(it.name, SearchEngines.isValidTemplate(it.template)) }
    @Test fun customTemplateValidation() {
        assertTrue(SearchEngines.isValidTemplate("https://s.example/?q=%s"))
        assertFalse(SearchEngines.isValidTemplate("http://s.example/?q=%s"))
        assertFalse(SearchEngines.isValidTemplate("https://s.example/?q="))
        assertFalse(SearchEngines.isValidTemplate("https://s.example/?q=%s&r=%s"))
        assertFalse(SearchEngines.isValidTemplate("javascript:%s"))
    }
    @Test fun templateForFallsBackWhenCustomInvalid() = assertEquals(SearchEngines.all.first().template, SearchEngines.templateFor(SearchEngines.CUSTOM_ID, "nonsense"))
    @Test fun unknownIdFallsBack() = assertEquals(SearchEngines.all.first().template, SearchEngines.templateFor("nope", ""))
}

class HistoryGroupingTest {
    private val utc = ZoneOffset.UTC
    private fun ms(y: Int, m: Int, d: Int, h: Int) = LocalDate.of(y, m, d).atStartOfDay(utc).plusHours(h.toLong()).toInstant().toEpochMilli()
    @Test fun groupsByDayNewestFirstAndOrdersWithin() {
        val items = listOf(ms(2026, 1, 1, 10), ms(2026, 1, 2, 9), ms(2026, 1, 2, 18), ms(2025, 12, 31, 23))
        val g = groupByDay(items, utc) { it }
        assertEquals(listOf(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 1), LocalDate.of(2025, 12, 31)), g.map { it.first })
        assertEquals(listOf(ms(2026, 1, 2, 18), ms(2026, 1, 2, 9)), g[0].second)
    }
    @Test fun emptyInput() = assertTrue(groupByDay(emptyList<Long>(), utc) { it }.isEmpty())
}

class SettingsMigrationTest {
    @Test fun corruptedEnumValuesAreReset() {
        val p = mutablePreferencesOf()
        p[Keys.THEME] = "BANANA"; p[Keys.TOOLBAR] = "TOP"; p[Keys.PROTECTION] = "???"
        SettingsMigrations.upgrade(p)
        assertNull(p[Keys.THEME]); assertEquals("TOP", p[Keys.TOOLBAR]); assertNull(p[Keys.PROTECTION])
        assertEquals(SettingsMigrations.CURRENT, p[Keys.VERSION])
        val s = SettingsStore.fromPrefs(p)
        assertEquals(ThemeMode.DARK, s.themeMode); assertEquals(ToolbarPosition.TOP, s.toolbarPosition); assertEquals(ProtectionLevel.BALANCED, s.protection)
    }
    @Test fun defaultsWhenEmpty() {
        val s = SettingsStore.fromPrefs(mutablePreferencesOf())
        assertFalse(s.onboardingDone); assertEquals(SearchEngines.DEFAULT_ID, s.searchEngineId); assertEquals(Workspaces.PERSONAL, s.currentWorkspaceId)
        assertTrue(s.httpsFirst); assertFalse(s.remoteSuggestions); assertTrue(s.recordHistory)
    }
    @Test fun upgradeIsIdempotent() {
        val p = mutablePreferencesOf(); p[Keys.ACCENT] = "AURORA"
        SettingsMigrations.upgrade(p); SettingsMigrations.upgrade(p)
        assertEquals("AURORA", p[Keys.ACCENT])
    }
}

class TabMapperTest {
    private fun row(id: String, url: String = "https://a.example", pos: Int = 0, ws: String = "personal", group: String? = null, state: String? = null) =
        TabEntity(id, url, "t", ws, group, false, false, pos, 0, 0, state)

    @Test fun dropsBlankDuplicateAndNonWebRows() {
        val r = TabMapper.restore(listOf(row("a"), row("a"), row(""), row("b", url = "javascript:1"), row("c", url = "file:///x"), row("d", url = "")), emptySet(), setOf("personal"))
        assertEquals(listOf("a", "d"), r.tabs.map { it.id })
        assertTrue(r.tabs.first { it.id == "d" }.showHome); assertFalse(r.tabs.first { it.id == "a" }.showHome)
    }
    @Test fun unknownGroupAndWorkspaceAreRepaired() {
        val t = TabMapper.restore(listOf(row("a", ws = "gone", group = "ghost")), setOf("real"), setOf("personal")).tabs.single()
        assertNull(t.groupId); assertEquals("personal", t.workspaceId)
    }
    @Test fun keepsValidGroup() = assertEquals("g", TabMapper.restore(listOf(row("a", group = "g")), setOf("g"), setOf("personal")).tabs.single().groupId)
    @Test fun oversizedStateDropped() {
        val r = TabMapper.restore(listOf(row("a", state = "x".repeat(TabMapper.MAX_STATE_CHARS + 1)), row("b", state = "ok")), emptySet(), setOf("personal"))
        assertFalse(r.states.containsKey("a")); assertEquals("ok", r.states["b"])
    }
    @Test fun totalStateBudgetEnforced() {
        val big = "x".repeat(TabMapper.MAX_STATE_CHARS)
        val rows = (0 until 20).map { row("t$it", pos = it, state = big) }
        val r = TabMapper.restore(rows, emptySet(), setOf("personal"))
        assertTrue(r.states.values.sumOf { it.length } <= TabMapper.MAX_TOTAL_STATE_CHARS); assertEquals(20, r.tabs.size)
    }
    @Test fun respectsPositionOrder() = assertEquals(listOf("b", "a"), TabMapper.restore(listOf(row("a", pos = 5), row("b", pos = 1)), emptySet(), setOf("personal")).tabs.map { it.id })
    @Test fun blankTabsNeverCarryState() = assertTrue(TabMapper.restore(listOf(row("a", url = "", state = "s")), emptySet(), setOf("personal")).states.isEmpty())
}

class TranslateLanguagesTest {
    @Test fun buildsEncodedUrl() {
        val u = com.wyrix.browser.core.TranslateLanguages.buildUrl("https://a.example/p?x=1&y=2", "fr")!!
        assertTrue(u.startsWith("https://translate.google.com/translate?sl=auto&tl=fr&u="))
        assertTrue(u.contains("https%3A%2F%2Fa.example%2Fp%3Fx%3D1%26y%3D2"))
    }
    @Test fun rejectsUnsupportedLanguageAndBadUrls() {
        val t = com.wyrix.browser.core.TranslateLanguages
        assertNull(t.buildUrl("https://a.example", "xx")); assertNull(t.buildUrl("javascript:1", "fr")); assertNull(t.buildUrl("https://u:p@a.example/", "fr"))
    }
    @Test fun defaultFallsBackToEnglish() {
        val t = com.wyrix.browser.core.TranslateLanguages
        assertEquals("de", t.defaultFor("DE")); assertEquals("en", t.defaultFor("sw"))
    }
}
