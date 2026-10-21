package com.wyrix.browser.ui.settings

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wyrix.browser.BrowserViewModel
import com.wyrix.browser.Screen
import com.wyrix.browser.core.SearchEngines
import com.wyrix.browser.data.*
import com.wyrix.browser.ui.common.*
import com.wyrix.browser.ui.library.ClearDialog
import com.wyrix.browser.security.BiometricGate
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.font.FontWeight

@Composable
fun SettingsScreen(vm: BrowserViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val st = s ?: return
    val ctx = LocalContext.current
    var dialog by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var customSearch by remember { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) vm.importBookmarks(ctx, u) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { u -> if (u != null) vm.exportBookmarks(ctx, u) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    /** Turning a lock on requires a successful unlock first, so it can't be enabled by accident or without working credentials. */
    fun setLock(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, on: Boolean) {
        if (!on) { vm.setPref(key, false); return }
        when (BiometricGate.availability(ctx)) {
            BiometricGate.Availability.AVAILABLE -> {
                val a = ctx as? android.app.Activity
                if (a == null) vm.toast("Can't show the unlock prompt here")
                else BiometricGate.authenticate(a, "Turn on lock", "Confirm it's you") { ok -> if (ok) vm.setPref(key, true) else vm.toast("Lock not turned on") }
            }
            BiometricGate.Availability.NOT_ENROLLED -> vm.toast("Set up a fingerprint, face or screen lock in Android Settings first")
            BiometricGate.Availability.UNAVAILABLE -> vm.toast("This device can't use biometric or screen-lock authentication")
        }
    }

    fun row(title: String, value: String, key: String) = @Composable {
        ListItem(headlineContent = { Text(title) }, supportingContent = { Text(value) }, modifier = Modifier.clickable { dialog = key }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
    }
    val engineName = if (st.searchEngineId == SearchEngines.CUSTOM_ID) "Custom" else SearchEngines.byId(st.searchEngineId)?.name ?: "DuckDuckGo"

    ScreenScaffold("Settings", onBack = { vm.closeScreen() }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item { SectionHeader("Default browser") }
            item {
                ListItem(headlineContent = { Text("Make WYRIX your default browser") }, supportingContent = { Text("Opens links from other apps in WYRIX") },
                    modifier = Modifier.clickable {
                        if (Build.VERSION.SDK_INT >= 29) {
                            val rm = ctx.getSystemService(RoleManager::class.java)
                            if (rm.isRoleAvailable(RoleManager.ROLE_BROWSER) && !rm.isRoleHeld(RoleManager.ROLE_BROWSER)) roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
                            else ctx.startActivity(Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                        } else ctx.startActivity(Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                    }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
            item { SectionHeader("Search") }
            item { row("Search engine", engineName, "engine")() }
            item { SettingSwitch("HTTPS first", "Try secure connections for typed addresses; HTTP only if you choose it after a failure", st.httpsFirst) { vm.setPref(Keys.HTTPS_FIRST, it) } }
            item {
                SettingSwitch("Search suggestions from the network", if (SearchEngines.byId(st.searchEngineId)?.suggestUrl != null) "Sends what you type to $engineName. Never used in private tabs." else "Not available for this search engine", st.remoteSuggestions,
                    enabled = SearchEngines.byId(st.searchEngineId)?.suggestUrl != null) { vm.setPref(Keys.REMOTE_SUGGESTIONS, it) }
            }
            item { SectionHeader("Privacy and security") }
            item { row("Tracking protection", st.protection.name.lowercase().replaceFirstChar { it.uppercase() }, "protection")() }
            item { SettingSwitch("Save browsing history", "Normal tabs only; private tabs never save history", st.recordHistory) { vm.setPref(Keys.RECORD_HISTORY, it) } }
            item { SettingSwitch("Hide private tabs from screenshots and recents", null, st.secureWindowInPrivate) { vm.setPref(Keys.SECURE_WINDOW, it) } }
            item { SettingSwitch("Limit pop-ups", "Allow at most a few new windows per minute, only from the visible tab", st.limitPopups) { vm.setPref(Keys.LIMIT_POPUPS, it) } }
            item { SettingSwitch("Open links from other apps in a new tab", null, st.openLinksInNewTab) { vm.setPref(Keys.NEW_TAB_LINKS, it) } }
            item { SectionHeader("App lock") }
            item { SettingSwitch("Lock WYRIX", "Ask for fingerprint, face or screen lock when you return to the app. Also hides it in recent apps.", st.lockApp) { setLock(Keys.LOCK_APP, it) } }
            item { SettingSwitch("Lock private tabs", "Ask before private tabs are shown", st.lockPrivate) { setLock(Keys.LOCK_PRIVATE, it) } }
            item {
                val label = when (st.lockTimeoutSec) { 0 -> "Immediately"; 30 -> "After 30 seconds"; 60 -> "After 1 minute"; 300 -> "After 5 minutes"; else -> "After ${st.lockTimeoutSec} seconds" }
                row("Lock after leaving the app", label, "lockTimeout")()
            }
            item { SectionHeader("Site permissions and protection") }
            item {
                val hosts = st.sitePermissions.map { it.substringBefore('|') }.toSet().size
                ListItem(headlineContent = { Text("Site permissions") }, supportingContent = { Text(if (hosts == 0) "No sites have saved permissions" else "$hosts sites") },
                    modifier = Modifier.clickable { dialog = "sitePerms" }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
            item {
                ListItem(headlineContent = { Text("Sites with tracking protection off") }, supportingContent = { Text(if (st.trackingExceptionHosts.isEmpty()) "None" else "${st.trackingExceptionHosts.size} sites") },
                    modifier = Modifier.clickable { dialog = "trackingExceptions" }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
            item { SectionHeader("Clear data") }
            item { ListItem(headlineContent = { Text("Clear history…") }, modifier = Modifier.clickable { dialog = "clearHistory" }, colors = ListItemDefaults.colors(containerColor = Color.Transparent)) }
            item { ListItem(headlineContent = { Text("Clear cookies and site data") }, modifier = Modifier.clickable { confirm = "cookies" }, colors = ListItemDefaults.colors(containerColor = Color.Transparent)) }
            item { ListItem(headlineContent = { Text("Clear cache") }, modifier = Modifier.clickable { confirm = "cache" }, colors = ListItemDefaults.colors(containerColor = Color.Transparent)) }
            item { ListItem(headlineContent = { Text("Close all private tabs") }, modifier = Modifier.clickable { vm.closeAllPrivate() }, colors = ListItemDefaults.colors(containerColor = Color.Transparent)) }
            item { SectionHeader("Appearance") }
            item { row("Theme", st.themeMode.name.lowercase().replaceFirstChar { it.uppercase() }, "theme")() }
            item { row("Accent", st.accent.label, "accent")() }
            item { SettingSwitch("True black (AMOLED)", null, st.trueBlack) { vm.setPref(Keys.TRUE_BLACK, it) } }
            item { SettingSwitch("High contrast", null, st.highContrast) { vm.setPref(Keys.HIGH_CONTRAST, it) } }
            item { SettingSwitch("Reduce motion", "Also follows Android battery saver", st.reducedMotion) { vm.setPref(Keys.REDUCED_MOTION, it) } }
            item { row("Toolbar position", st.toolbarPosition.name.lowercase().replaceFirstChar { it.uppercase() }, "toolbar")() }
            item { row("Home layout", st.homeLayout.name.lowercase().replaceFirstChar { it.uppercase() }, "home")() }
            item { ListItem(headlineContent = { Text("Reset appearance to defaults") }, modifier = Modifier.clickable { vm.resetAppearance() }, colors = ListItemDefaults.colors(containerColor = Color.Transparent)) }
            item { SectionHeader("Bookmarks") }
            item { ListItem(headlineContent = { Text("Import bookmarks (HTML)") }, supportingContent = { Text("Export from your old browser as an HTML file, then pick it here") }, modifier = Modifier.clickable { importer.launch(arrayOf("text/html", "text/plain", "*/*")) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent)) }
            item { ListItem(headlineContent = { Text("Export bookmarks (HTML)") }, modifier = Modifier.clickable { exporter.launch("wyrix-bookmarks.html") }, colors = ListItemDefaults.colors(containerColor = Color.Transparent)) }
            item { SectionHeader("About") }
            item { ListItem(headlineContent = { Text("About, licenses and help") }, modifier = Modifier.clickable { vm.openScreen(Screen.ABOUT) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent)) }
        }
    }

    when (dialog) {
        "engine" -> ChoiceDialog("Search engine", SearchEngines.all.map { it.id to it.name } + (SearchEngines.CUSTOM_ID to "Custom…"), st.searchEngineId,
            { id -> if (id == SearchEngines.CUSTOM_ID) customSearch = true else vm.setPref(Keys.SEARCH_ENGINE, id) }) { dialog = null }
        "protection" -> ChoiceDialog("Tracking protection", listOf(ProtectionLevel.OFF to "Off", ProtectionLevel.BALANCED to "Balanced (recommended)", ProtectionLevel.STRICT to "Strict (may break some sites)"), st.protection, { vm.setPref(Keys.PROTECTION, it.name) }) { dialog = null }
        "theme" -> ChoiceDialog("Theme", ThemeMode.entries.map { it to it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }, st.themeMode, { vm.setPref(Keys.THEME, it.name) }) { dialog = null }
        "accent" -> ChoiceDialog("Accent", AccentPreset.entries.map { it to it.label }, st.accent, { vm.setPref(Keys.ACCENT, it.name) }) { dialog = null }
        "toolbar" -> ChoiceDialog("Toolbar position", ToolbarPosition.entries.map { it to it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }, st.toolbarPosition, { vm.setPref(Keys.TOOLBAR, it.name) }) { dialog = null }
        "home" -> ChoiceDialog("Home layout", HomeLayout.entries.map { it to it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }, st.homeLayout, { vm.setPref(Keys.HOME_LAYOUT, it.name) }) { dialog = null }
        "clearHistory" -> ClearDialog({ dialog = null }) { since -> vm.clearHistory(since); dialog = null }
        "lockTimeout" -> ChoiceDialog("Lock after leaving the app", listOf(0 to "Immediately", 30 to "After 30 seconds", 60 to "After 1 minute", 300 to "After 5 minutes"), st.lockTimeoutSec, { vm.setPref(Keys.LOCK_TIMEOUT, it) }) { dialog = null }
        "sitePerms" -> SitePermissionsDialog(st, vm) { dialog = null }
        "trackingExceptions" -> AlertDialog(
            onDismissRequest = { dialog = null }, title = { Text("Tracking protection off") },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(st.trackingExceptionHosts.sorted(), key = { it }) { h ->
                        ListItem(headlineContent = { Text(h) }, trailingContent = { TextButton(onClick = { vm.removeTrackingException(h) }) { Text("Turn on") } }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                    }
                    if (st.trackingExceptionHosts.isEmpty()) item { Text("No sites. Use the lock icon in the address bar on a page to relax protection for just that site.") }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Close") } },
        )
    }
    if (customSearch) {
        var t by remember { mutableStateOf(st.customSearchTemplate.ifBlank { "https://example.com/search?q=%s" }) }
        AlertDialog(onDismissRequest = { customSearch = false }, title = { Text("Custom search") },
            text = { Column { OutlinedTextField(t, { t = it.take(512) }, label = { Text("HTTPS URL with %s") }, singleLine = true, isError = !SearchEngines.isValidTemplate(t))
                Text("Use %s where the search words go. Must start with https://", style = MaterialTheme.typography.bodySmall) } },
            confirmButton = { TextButton(enabled = SearchEngines.isValidTemplate(t), onClick = { vm.setPref(Keys.CUSTOM_SEARCH, t); vm.setPref(Keys.SEARCH_ENGINE, SearchEngines.CUSTOM_ID); customSearch = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { customSearch = false }) { Text("Cancel") } })
    }
    when (confirm) {
        "cookies" -> ConfirmDialog("Clear cookies and site data?", "You'll be signed out of most sites.", "Clear", { vm.clearCookies(); confirm = null }, { confirm = null })
        "cache" -> ConfirmDialog("Clear cache?", "Pages may load slower once.", "Clear", { vm.clearCache(); confirm = null }, { confirm = null })
    }
}

@Composable
private fun SitePermissionsDialog(st: Settings, vm: BrowserViewModel, onDismiss: () -> Unit) {
    val byHost = st.sitePermissions.mapNotNull { e -> e.split('|').takeIf { it.size == 3 } }.groupBy({ it[0] }, { it[1] + ": " + it[2] }).toSortedMap()
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Site permissions") },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(byHost.entries.toList(), key = { it.key }) { (host, perms) ->
                    val pending = host in st.sitePermResetHosts
                    ListItem(
                        headlineContent = { Text(host, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(perms.joinToString(", ") + if (pending) "\nWill reset next time you open this site" else "") },
                        trailingContent = { if (!pending) TextButton(onClick = { vm.resetSitePermissions(host) }) { Text("Reset") } },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
                if (byHost.isEmpty()) item { Text("Sites you allow or block (location, autoplay, storage…) are listed here after you visit them.") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
