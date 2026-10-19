package com.wyrix.browser.data

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.toMutablePreferences
import androidx.datastore.preferences.preferencesDataStore
import com.wyrix.browser.core.SearchEngines
import com.wyrix.browser.tabs.Workspaces
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ToolbarPosition { TOP, BOTTOM }
enum class ProtectionLevel { OFF, BALANCED, STRICT }
enum class TabLayoutMode { LIST, GRID, COMPACT }
enum class HomeLayout { COMPACT, STANDARD, EXPANDED }
enum class AccentPreset(val label: String) { ELECTRIC("Electric"), ION("Ion"), AURORA("Aurora"), EMBER("Ember") }

data class Settings(
    val onboardingDone: Boolean = false,
    val searchEngineId: String = SearchEngines.DEFAULT_ID,
    val customSearchTemplate: String = "",
    val themeMode: ThemeMode = ThemeMode.DARK,
    val trueBlack: Boolean = false,
    val accent: AccentPreset = AccentPreset.ELECTRIC,
    val highContrast: Boolean = false,
    val reducedMotion: Boolean = false,
    val toolbarPosition: ToolbarPosition = ToolbarPosition.BOTTOM,
    val tabLayout: TabLayoutMode = TabLayoutMode.GRID,
    val homeLayout: HomeLayout = HomeLayout.STANDARD,
    val showShortcuts: Boolean = true,
    val showBookmarks: Boolean = true,
    val showFrequent: Boolean = true,
    val showPrivacyStats: Boolean = true,
    val protection: ProtectionLevel = ProtectionLevel.BALANCED,
    val recordHistory: Boolean = true,
    val httpsFirst: Boolean = true,
    val remoteSuggestions: Boolean = false,
    val secureWindowInPrivate: Boolean = true,
    val limitPopups: Boolean = true,
    val openLinksInNewTab: Boolean = false,
    val desktopHosts: Set<String> = emptySet(),
    val blockedTotal: Long = 0,
    val currentWorkspaceId: String = Workspaces.PERSONAL,
    val selectedTabId: String? = null,
)

object Keys {
    val VERSION = intPreferencesKey("schema_version")
    val ONBOARDING = booleanPreferencesKey("onboarding_done")
    val SEARCH_ENGINE = stringPreferencesKey("search_engine")
    val CUSTOM_SEARCH = stringPreferencesKey("custom_search")
    val THEME = stringPreferencesKey("theme_mode")
    val TRUE_BLACK = booleanPreferencesKey("true_black")
    val ACCENT = stringPreferencesKey("accent")
    val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
    val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
    val TOOLBAR = stringPreferencesKey("toolbar_position")
    val TAB_LAYOUT = stringPreferencesKey("tab_layout")
    val HOME_LAYOUT = stringPreferencesKey("home_layout")
    val SHOW_SHORTCUTS = booleanPreferencesKey("home_shortcuts")
    val SHOW_BOOKMARKS = booleanPreferencesKey("home_bookmarks")
    val SHOW_FREQUENT = booleanPreferencesKey("home_frequent")
    val SHOW_STATS = booleanPreferencesKey("home_stats")
    val PROTECTION = stringPreferencesKey("protection")
    val RECORD_HISTORY = booleanPreferencesKey("record_history")
    val HTTPS_FIRST = booleanPreferencesKey("https_first")
    val REMOTE_SUGGESTIONS = booleanPreferencesKey("remote_suggestions")
    val SECURE_WINDOW = booleanPreferencesKey("secure_window_private")
    val LIMIT_POPUPS = booleanPreferencesKey("limit_popups")
    val NEW_TAB_LINKS = booleanPreferencesKey("open_links_new_tab")
    val DESKTOP_HOSTS = stringSetPreferencesKey("desktop_hosts")
    val BLOCKED_TOTAL = longPreferencesKey("blocked_total")
    val WORKSPACE = stringPreferencesKey("current_workspace")
    val SELECTED_TAB = stringPreferencesKey("selected_tab")

    val APPEARANCE: List<Preferences.Key<*>> = listOf(THEME, TRUE_BLACK, ACCENT, HIGH_CONTRAST, REDUCED_MOTION, TOOLBAR, TAB_LAYOUT, HOME_LAYOUT)
}

/** Repairs corrupted/outdated preference values. Bump CURRENT and extend [upgrade] on schema change. */
object SettingsMigrations {
    const val CURRENT = 1

    private val ENUM_KEYS: Map<Preferences.Key<String>, (String) -> Boolean> = mapOf(
        Keys.THEME to { v: String -> ThemeMode.entries.any { it.name == v } },
        Keys.ACCENT to { v: String -> AccentPreset.entries.any { it.name == v } },
        Keys.TOOLBAR to { v: String -> ToolbarPosition.entries.any { it.name == v } },
        Keys.TAB_LAYOUT to { v: String -> TabLayoutMode.entries.any { it.name == v } },
        Keys.HOME_LAYOUT to { v: String -> HomeLayout.entries.any { it.name == v } },
        Keys.PROTECTION to { v: String -> ProtectionLevel.entries.any { it.name == v } },
    )

    fun upgrade(prefs: MutablePreferences) {
        for ((key, valid) in ENUM_KEYS) {
            val v = prefs[key]
            if (v != null && !valid(v)) prefs.remove(key) // falls back to the default
        }
        prefs[Keys.VERSION] = CURRENT
    }
}

private val migration = object : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences) = (currentData[Keys.VERSION] ?: 0) < SettingsMigrations.CURRENT
    override suspend fun migrate(currentData: Preferences): Preferences {
        val m = currentData.toMutablePreferences()
        SettingsMigrations.upgrade(m)
        return m
    }
    override suspend fun cleanUp() {}
}

private val Context.settingsDataStore by preferencesDataStore(name = "wyrix_settings", produceMigrations = { listOf(migration) })

private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    this?.let { s -> enumValues<E>().firstOrNull { it.name == s } } ?: default

class SettingsStore(private val context: Context) {
    val settings: Flow<Settings> = context.settingsDataStore.data.map { p -> fromPrefs(p) }

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.settingsDataStore.edit { it[key] = value }
    }

    suspend fun remove(vararg keys: Preferences.Key<*>) {
        context.settingsDataStore.edit { p -> keys.forEach { p.remove(it) } }
    }

    suspend fun addBlocked(n: Long) {
        context.settingsDataStore.edit { it[Keys.BLOCKED_TOTAL] = (it[Keys.BLOCKED_TOTAL] ?: 0L) + n }
    }

    suspend fun setSelectedTab(id: String?) {
        context.settingsDataStore.edit { if (id == null) it.remove(Keys.SELECTED_TAB) else it[Keys.SELECTED_TAB] = id }
    }

    suspend fun updateDesktopHost(host: String, enabled: Boolean) {
        context.settingsDataStore.edit {
            val cur = it[Keys.DESKTOP_HOSTS].orEmpty()
            it[Keys.DESKTOP_HOSTS] = if (enabled) cur + host else cur - host
        }
    }

    companion object {
        fun fromPrefs(p: Preferences): Settings {
            val d = Settings()
            return Settings(
                onboardingDone = p[Keys.ONBOARDING] ?: d.onboardingDone,
                searchEngineId = p[Keys.SEARCH_ENGINE] ?: d.searchEngineId,
                customSearchTemplate = p[Keys.CUSTOM_SEARCH] ?: d.customSearchTemplate,
                themeMode = p[Keys.THEME].toEnum(d.themeMode),
                trueBlack = p[Keys.TRUE_BLACK] ?: d.trueBlack,
                accent = p[Keys.ACCENT].toEnum(d.accent),
                highContrast = p[Keys.HIGH_CONTRAST] ?: d.highContrast,
                reducedMotion = p[Keys.REDUCED_MOTION] ?: d.reducedMotion,
                toolbarPosition = p[Keys.TOOLBAR].toEnum(d.toolbarPosition),
                tabLayout = p[Keys.TAB_LAYOUT].toEnum(d.tabLayout),
                homeLayout = p[Keys.HOME_LAYOUT].toEnum(d.homeLayout),
                showShortcuts = p[Keys.SHOW_SHORTCUTS] ?: d.showShortcuts,
                showBookmarks = p[Keys.SHOW_BOOKMARKS] ?: d.showBookmarks,
                showFrequent = p[Keys.SHOW_FREQUENT] ?: d.showFrequent,
                showPrivacyStats = p[Keys.SHOW_STATS] ?: d.showPrivacyStats,
                protection = p[Keys.PROTECTION].toEnum(d.protection),
                recordHistory = p[Keys.RECORD_HISTORY] ?: d.recordHistory,
                httpsFirst = p[Keys.HTTPS_FIRST] ?: d.httpsFirst,
                remoteSuggestions = p[Keys.REMOTE_SUGGESTIONS] ?: d.remoteSuggestions,
                secureWindowInPrivate = p[Keys.SECURE_WINDOW] ?: d.secureWindowInPrivate,
                limitPopups = p[Keys.LIMIT_POPUPS] ?: d.limitPopups,
                openLinksInNewTab = p[Keys.NEW_TAB_LINKS] ?: d.openLinksInNewTab,
                desktopHosts = p[Keys.DESKTOP_HOSTS] ?: d.desktopHosts,
                blockedTotal = p[Keys.BLOCKED_TOTAL] ?: d.blockedTotal,
                currentWorkspaceId = p[Keys.WORKSPACE] ?: d.currentWorkspaceId,
                selectedTabId = p[Keys.SELECTED_TAB],
            )
        }
    }
}
