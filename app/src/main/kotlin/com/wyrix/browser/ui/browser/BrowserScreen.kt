package com.wyrix.browser.ui.browser

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wyrix.browser.BrowserViewModel
import com.wyrix.browser.Screen
import com.wyrix.browser.Suggestion
import com.wyrix.browser.core.SearchEngines
import com.wyrix.browser.data.ToolbarPosition
import com.wyrix.browser.ui.home.HomeScreen
import com.wyrix.browser.ui.library.BookmarksScreen
import com.wyrix.browser.ui.library.DownloadsScreen
import com.wyrix.browser.ui.library.HistoryScreen
import com.wyrix.browser.ui.settings.AboutScreen
import com.wyrix.browser.ui.settings.SettingsScreen
import com.wyrix.browser.ui.tabs.TabSwitcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import com.wyrix.browser.data.Keys
import com.wyrix.browser.ui.common.WyrixIcons
import com.wyrix.browser.ui.reader.ReaderScreen
import com.wyrix.browser.ui.reader.SavedArticlesScreen
import com.wyrix.browser.ui.security.LockOverlay
import org.mozilla.geckoview.GeckoView

@Composable
fun BrowserScreen(vm: BrowserViewModel) {
    val ctx = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val tabsState by vm.tabs.collectAsStateWithLifecycle()
    val screen by vm.screen.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val fullscreen by vm.fullscreen.collectAsStateWithLifecycle()
    val privateMode by vm.privateMode.collectAsStateWithLifecycle()
    val ctxMenu by vm.contextMenu.collectAsStateWithLifecycle()
    val s = settings ?: return
    val tab = tabsState.selected
    val locked by vm.locked.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var geckoView by remember { mutableStateOf<GeckoView?>(null) }
    var translateDialog by remember { mutableStateOf(false) }

    // Android runtime permission bridge for camera/mic/location requested by pages.
    var pendingPerm by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        pendingPerm?.invoke(r.values.all { it }); pendingPerm = null
    }
    DisposableEffect(Unit) {
        vm.setAndroidPermissionRequester { perms, cb -> pendingPerm = cb; permLauncher.launch(perms) }
        onDispose { vm.setAndroidPermissionRequester(null) }
    }
    LaunchedEffect(Unit) {
        vm.toasts.collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(Unit) {
        vm.externalIntents.collect { p ->
            try { ctx.startActivity(p.intent) } catch (e: Exception) {
                val fb = p.fallbackUrl
                if (fb != null) vm.loadUrl(fb) else Toast.makeText(ctx, "No app can open this link", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var editing by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var showSiteInfo by remember { mutableStateOf(false) }
    var bookmarked by remember { mutableStateOf(false) }
    LaunchedEffect(tab?.url, tab?.id) { bookmarked = tab?.url?.let { vm.bookmarkFor(it) != null } ?: false }
    // re-check shortly after toggles (bookmark toggle is async)
    fun startEdit() {
        val url = if (tab?.showHome == false) tab.url else ""
        field = TextFieldValue(url, TextRange(0, url.length)); editing = true
    }
    fun submit(text: String) { editing = false; vm.submitAddress(text) }

    BackHandler(enabled = editing || fullscreen || screen != Screen.BROWSER || tab != null) {
        if (editing) editing = false else if (!vm.handleBack()) (ctx as? android.app.Activity)?.moveTaskToBack(true)
    }

    val top = s.toolbarPosition == ToolbarPosition.TOP
    val chrome: @Composable () -> Unit = {
        Column(Modifier.background(MaterialTheme.colorScheme.background)) {
            AddressRow(
                tab = tab, editing = editing, field = field, onField = { field = it }, onStartEdit = { startEdit() },
                onSubmit = { submit(field.text) }, onSiteInfo = { showSiteInfo = true },
                onReloadOrStop = { if (tab?.loading == true) vm.stop() else vm.reload() }, bookmarked = bookmarked,
                onToggleBookmark = { vm.toggleBookmark(); bookmarked = !bookmarked },
                onCopy = { tab?.let { copyText(ctx, it.url); vm.toast("Link copied") } },
                onShare = { tab?.let { shareText(ctx, it.url) } },
                onPasteGo = { val t = readClipboard(ctx); if (t == null) vm.toast("Nothing to paste") else submit(t) },
                onReloadHard = { vm.hardReload() },
            )
            ActionRow(tab, tabsState.inScope(privateMode, s.currentWorkspaceId).size, privateMode,
                canReopen = tabsState.recentlyClosed.isNotEmpty(),
                onBack = { vm.handleBack() }, onForward = { vm.goForward() }, onHome = { editing = false; vm.goHome() },
                onTabs = { editing = false; scope.launch { geckoView?.let { vm.captureThumbnail(it) }; vm.openScreen(Screen.TABS) } },
                onMenu = { BrowserMenu(vm, tab, privateMode, onShare = { shareText(ctx, it) }, onTranslate = { translateDialog = true }) },
                onBackLong = { vm.openScreen(Screen.HISTORY) }, onHomeLong = { vm.newTab(privateMode) },
                onNewTab = { vm.newTab(false) }, onNewPrivateTab = { vm.newTab(true) },
                onCloseTab = { tab?.let { vm.closeTab(it.id) } }, onReopen = { vm.reopenClosed() })
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().then(if (fullscreen) Modifier else Modifier.windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)).imePadding())) {
            if (!fullscreen && top) chrome()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                GeckoViewHost(if (locked) null else session, vm.runtime, Modifier.fillMaxSize(), onView = { geckoView = it })
                if (tab != null && (tab.showHome || tab.url.isBlank())) HomeScreen(vm, onStartEdit = { startEdit() })
                if (tab != null && (tab.error != null || tab.crashed) && !tab.showHome) ErrorOverlay(tab, vm)
                if (editing) {
                    var sugg by remember { mutableStateOf<List<Suggestion>>(emptyList()) }
                    LaunchedEffect(field.text) {
                        delay(120)
                        sugg = vm.suggest(field.text)
                        val remote = vm.remoteSuggest(field.text.trim())
                        if (remote.isNotEmpty()) sugg = (sugg + remote).distinctBy { it.action }
                    }
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.fillMaxSize()) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text("Search with", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                SearchEngines.all.forEach { e ->
                                    FilterChip(selected = s.searchEngineId == e.id, onClick = { vm.setPref(Keys.SEARCH_ENGINE, e.id) }, label = { Text(e.name) })
                                }
                            }
                            if (field.text.isBlank()) {
                                // The clipboard is only read when the user taps this row (never automatically).
                                ListItem(headlineContent = { Text("Paste and go") }, supportingContent = { Text("Open what you copied") },
                                    leadingContent = { Icon(WyrixIcons.Copy, null) },
                                    modifier = Modifier.clickable { val t = readClipboard(ctx); if (t == null) vm.toast("Nothing to paste") else submit(t) })
                            }
                            SuggestionList(sugg, field.text.trim(), SearchEngines.byId(s.searchEngineId)?.name ?: "Custom search",
                                onPick = { sg -> editing = false; if (sg.tabId != null) vm.selectTab(sg.tabId) else submit(sg.action) },
                                onSearch = { submit(field.text) }, modifier = Modifier.weight(1f).fillMaxWidth())
                        }
                    }
                }
            }
            if (!fullscreen && !top) chrome()
        }
        if (fullscreen) FilledTonalButton(onClick = { vm.exitFullscreen() }, modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd).windowInsetsPadding(WindowInsets.systemBars).padding(8.dp)) { Text("Exit full screen") }

        when (screen) {
            Screen.TABS -> TabSwitcher(vm)
            Screen.BOOKMARKS -> BookmarksScreen(vm)
            Screen.HISTORY -> HistoryScreen(vm)
            Screen.DOWNLOADS -> DownloadsScreen(vm)
            Screen.SETTINGS -> SettingsScreen(vm)
            Screen.ABOUT -> AboutScreen(vm)
            Screen.READER -> ReaderScreen(vm)
            Screen.SAVED -> SavedArticlesScreen(vm)
            Screen.BROWSER -> {}
        }
        if (!locked) {
            PromptHost(vm)
            ctxMenu?.let { ContextSheet(it, vm) { link -> shareText(ctx, link) } }
            if (showSiteInfo && tab != null) SiteInfoSheet(tab, vm) { showSiteInfo = false }
            if (translateDialog && tab != null) TranslateDialog(tab, s.translateLang, vm) { translateDialog = false }
        }
        if (locked) {
            BackHandler { (ctx as? android.app.Activity)?.moveTaskToBack(true) }
            LockOverlay(privateOnly = !s.lockApp, onUnlocked = { vm.unlock() })
        }
    }
}

@Composable
private fun BrowserMenu(vm: BrowserViewModel, tab: com.wyrix.browser.tabs.TabState?, privateMode: Boolean, onShare: (String) -> Unit, onTranslate: () -> Unit) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "More options") }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        @Composable fun Item(label: String, enabled: Boolean = true, action: () -> Unit) =
            DropdownMenuItem(text = { Text(label) }, enabled = enabled, onClick = { open = false; action() })
        val hasPage = tab != null && tab.hasContent && !tab.showHome
        Item("New tab") { vm.newTab(privateMode) }
        Item("New private tab") { vm.newTab(true) }
        HorizontalDivider()
        Item("Share page", hasPage) { tab?.let { onShare(it.url) } }
        Item("Copy link", hasPage) { tab?.let { copyText(ctx, it.url); vm.toast("Link copied") } }
        Item("Desktop site", hasPage) { vm.toggleDesktop() }
        Item("Reload without cache", hasPage) { vm.hardReload() }
        Item("Reader mode", hasPage && tab?.url?.startsWith("https://") == true) { vm.openReader() }
        Item("Translate page…", hasPage) { onTranslate() }
        Item("Saved articles") { vm.openSavedList() }
        Item("Add to home shortcuts", hasPage) { vm.addCurrentAsShortcut() }
        HorizontalDivider()
        Item("Bookmarks") { vm.openScreen(Screen.BOOKMARKS) }
        Item("History") { vm.openScreen(Screen.HISTORY) }
        Item("Downloads") { vm.openScreen(Screen.DOWNLOADS) }
        Item("Settings") { vm.openScreen(Screen.SETTINGS) }
        if (privateMode) { HorizontalDivider(); Item("Close all private tabs") { vm.closeAllPrivate() } }
    }
}
