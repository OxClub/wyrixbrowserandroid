package com.wyrix.browser

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.datastore.preferences.core.Preferences
import com.wyrix.browser.core.Resolved
import com.wyrix.browser.core.SearchEngines
import com.wyrix.browser.core.UrlParser
import com.wyrix.browser.core.UrlTools
import com.wyrix.browser.core.BookmarkHtml
import com.wyrix.browser.data.*
import com.wyrix.browser.engine.*
import com.wyrix.browser.tabs.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.mozilla.geckoview.GeckoSession
import java.util.UUID

enum class Screen { BROWSER, TABS, BOOKMARKS, HISTORY, DOWNLOADS, SETTINGS, ABOUT }

data class Suggestion(val kind: Kind, val title: String, val subtitle: String, val action: String, val tabId: String? = null) {
    enum class Kind { TAB, BOOKMARK, HISTORY, COMMAND, REMOTE, SEARCH }
}

/** Single source of truth for browser UI state; engine events are reduced into [tabs] here. */
@OptIn(FlowPreview::class)
class BrowserViewModel(private val app: WyrixApp) : ViewModel() {
    private val c = app.container
    private val sm = c.sessions
    private val store = c.settingsStore

    val settings: StateFlow<Settings?> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _tabs = MutableStateFlow(TabsState())
    val tabs: StateFlow<TabsState> = _tabs.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()
    private val _screen = MutableStateFlow(Screen.BROWSER)
    val screen: StateFlow<Screen> = _screen.asStateFlow()
    private val _session = MutableStateFlow<GeckoSession?>(null)
    val session: StateFlow<GeckoSession?> = _session.asStateFlow()
    private val _fullscreen = MutableStateFlow(false)
    val fullscreen: StateFlow<Boolean> = _fullscreen.asStateFlow()
    private val _requests = MutableStateFlow<List<PromptRequest>>(emptyList())
    val requests: StateFlow<List<PromptRequest>> = _requests.asStateFlow()
    private val _context = MutableStateFlow<ContextMenuInfo?>(null)
    val contextMenu: StateFlow<ContextMenuInfo?> = _context.asStateFlow()
    private val _sitePerms = MutableStateFlow<Map<String, List<SitePermissionInfo>>>(emptyMap())
    val sitePerms: StateFlow<Map<String, List<SitePermissionInfo>>> = _sitePerms.asStateFlow()
    private val _blockedSession = MutableStateFlow(0)
    val blockedSession: StateFlow<Int> = _blockedSession.asStateFlow()
    val toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val externalIntents = MutableSharedFlow<ExternalLinks.Prepared>(extraBufferCapacity = 2)

    val workspaces = c.organization.workspaces.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val groups = c.organization.groups.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val shortcuts = c.organization.shortcuts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val downloads = c.downloadDao.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val frequent = c.history.frequent(8).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentBookmarks = c.bookmarks.recent(8).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val privateMode: StateFlow<Boolean> = _tabs.map { it.selected?.isPrivate == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val secureWindow: StateFlow<Boolean> = combine(settings, privateMode) { s, p -> s?.secureWindowInPrivate == true && p }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val recorded = HashSet<String>()
    private var blockedUnflushed = 0
    private var lastShownSession: GeckoSession? = null

    val runtime get() = c.engine.runtime
    private val workspaceId get() = settings.value?.currentWorkspaceId ?: Workspaces.PERSONAL
    private fun now() = System.currentTimeMillis()

    init {
        sm.trackingProtectionOn = { settings.value?.protection != ProtectionLevel.OFF }
        sm.limitPopups = { settings.value?.limitPopups != false }
        sm.isTabSelected = { it == _tabs.value.selectedId }
        viewModelScope.launch { sm.events.collect { onEvent(it) } }
        viewModelScope.launch {
            settings.filterNotNull().map { it.protection }.distinctUntilChanged().collect { c.engine.applyProtection(it) }
        }
        viewModelScope.launch { restore() }
        viewModelScope.launch {
            _tabs.map { st -> st.tabs.filter { !it.isPrivate }.map { TabMapper.toEntity(it, 0, null) } to st.selectedId }
                .distinctUntilChanged().debounce(800).collect { persistNow() }
        }
        viewModelScope.launch { c.downloads.resumePollingIfNeeded() }
    }

    private suspend fun restore() {
        c.organization.ensureDefaults()
        val ws = c.organization.workspaces.first().map { it.id }.toSet()
        val restored = c.tabRepository.load(c.organization.allGroupIds(), ws)
        sm.seedStates(restored.states)
        val selectedPref = store.settings.first().selectedTabId
        var st = TabsState(tabs = restored.tabs, selectedId = restored.tabs.firstOrNull { it.id == selectedPref }?.id
            ?: restored.tabs.maxByOrNull { it.lastAccessed }?.id)
        if (st.selected == null) st = st.withTab(blankTab(false), true)
        _tabs.value = st
        syncSession()
        _ready.value = true
    }

    private fun blankTab(isPrivate: Boolean, workspace: String = workspaceId) =
        TabState(id = UUID.randomUUID().toString(), isPrivate = isPrivate, workspaceId = workspace, createdAt = now(), lastAccessed = now())

    private fun tab(id: String) = _tabs.value.tab(id)
    private fun update(id: String, f: (TabState) -> TabState) = _tabs.update { it.withUpdate(id, f) }
    fun toast(msg: String) { toasts.tryEmit(msg) }

    // ---- engine events -----------------------------------------------------------------------
    private fun onEvent(e: TabEvent) {
        val t = tab(e.tabId)
        if (t == null && e !is TabEvent.PopupOpened) return
        when (e) {
            is TabEvent.Location -> if (t != null && !(t.error != null && e.url.startsWith("data:")) && !e.url.startsWith("data:")) {
                update(e.tabId) { it.copy(url = e.url.ifBlank { it.url }) }
                _sitePerms.update { it + (e.tabId to e.permissions) }
            }
            is TabEvent.Title -> {
                update(e.tabId) { it.copy(title = e.title) }
                if (t != null && recorded.contains(e.tabId + t.url) && !t.isPrivate) viewModelScope.launch { c.history.updateTitle(t.url, e.title) }
            }
            is TabEvent.PageStart -> if (!e.url.startsWith("data:")) {
                recorded.removeAll { it.startsWith(e.tabId) }
                update(e.tabId) { it.copy(loading = true, progress = 5, error = null, crashed = false, blocked = 0) }
            }
            is TabEvent.PageStop -> {
                update(e.tabId) { it.copy(loading = false, progress = 0, httpFallback = if (e.success) null else it.httpFallback) }
                val cur = tab(e.tabId)
                val s = settings.value
                if (e.success && cur != null && !cur.isPrivate && s?.recordHistory == true && cur.error == null &&
                    UrlTools.isWebUrl(cur.url) && recorded.add(cur.id + cur.url)
                ) viewModelScope.launch { c.history.record(cur.url, cur.title) }
            }
            is TabEvent.Progress -> update(e.tabId) { it.copy(progress = e.progress) }
            is TabEvent.Nav -> update(e.tabId) { it.copy(canGoBack = e.canGoBack ?: it.canGoBack, canGoForward = e.canGoForward ?: it.canGoForward) }
            is TabEvent.Security -> update(e.tabId) { it.copy(secure = e.secure) }
            is TabEvent.LoadError -> update(e.tabId) {
                it.copy(loading = false, error = TabError(e.url, e.kind, e.code,
                    it.httpFallback?.takeIf { _ -> e.kind == ErrorKind.NETWORK || e.kind == ErrorKind.SECURITY }))
            }
            is TabEvent.CloseRequest -> closeTab(e.tabId)
            is TabEvent.FullScreen -> _fullscreen.value = e.on && _tabs.value.selectedId == e.tabId
            is TabEvent.Crashed -> { sm.forget(e.tabId); update(e.tabId) { it.copy(crashed = true, loading = false) }; syncSession() }
            is TabEvent.Blocked -> { update(e.tabId) { it.copy(blocked = it.blocked + 1) }; _blockedSession.update { it + 1 }; blockedUnflushed++ ; if (blockedUnflushed >= 25) flushBlocked() }
            is TabEvent.PopupOpened -> {
                val opener = tab(e.openerId)
                val nt = TabState(id = e.tabId, url = e.uri, isPrivate = e.isPrivate, workspaceId = opener?.workspaceId ?: workspaceId,
                    groupId = opener?.groupId, showHome = false, openerId = e.openerId, createdAt = now(), lastAccessed = now(), loading = true)
                if (_tabs.value.canAdd()) { _tabs.update { it.withTab(nt, true) }; syncSession() }
                else sm.remove(e.tabId)
            }
            is TabEvent.ContextMenu -> _context.value = ContextMenuInfo(e.tabId, e.linkUri, e.imageUri, t?.isPrivate == true)
            is TabEvent.Download -> startDownload(e, t?.isPrivate == true)
            is TabEvent.Request -> when (val r = e.request) {
                is PromptRequest.External -> {
                    val prepared = ExternalLinks.prepare(r.uri)
                    if (prepared == null) toast("Blocked a link this browser can't open")
                    else _requests.update { it + PromptRequest.External(r.tabId, r.uri) { ok -> if (ok) externalIntents.tryEmit(prepared) } }
                }
                else -> _requests.update { it + r }
            }
        }
    }

    private fun startDownload(e: TabEvent.Download, isPrivate: Boolean) {
        viewModelScope.launch {
            val out = c.downloads.enqueue(DownloadRequest(e.uri, e.fileNameHint, e.mime, e.contentLength, e.body, record = !isPrivate))
            when (out) {
                is DownloadOutcome.Started -> toast("Downloading ${out.fileName}")
                is DownloadOutcome.Unsupported -> toast(out.reason)
            }
        }
    }

    fun resolveRequest(r: PromptRequest, allow: Boolean) {
        _requests.update { it - r }
        when (r) {
            is PromptRequest.Alert -> r.done()
            is PromptRequest.Confirm -> r.result(allow)
            is PromptRequest.Permission -> r.result(allow)
            is PromptRequest.External -> r.result(allow)
            is PromptRequest.FilePick -> r.result(null)
        }
    }
    fun resolveFile(r: PromptRequest.FilePick, uris: List<Uri>?) { _requests.update { it - r }; r.result(uris) }
    private fun cancelRequestsFor(tabId: String) {
        val mine = _requests.value.filter { it.tabId == tabId }
        _requests.update { l -> l.filterNot { it.tabId == tabId } }
        mine.forEach { it.cancel() }
    }
    fun dismissContextMenu() { _context.value = null }

    // ---- session sync ------------------------------------------------------------------------
    private fun syncSession() {
        val t = _tabs.value.selected
        val ts = when {
            t == null -> null
            !t.showHome && t.hasContent && !t.crashed -> sm.ensure(t)
            else -> sm.peek(t.id)
        }
        _session.value = ts?.session
        if (_fullscreen.value && ts?.session !== lastShownSession) _fullscreen.value = false
        lastShownSession = ts?.session
    }

    // ---- navigation --------------------------------------------------------------------------
    fun searchTemplate(): String = settings.value?.let { SearchEngines.templateFor(it.searchEngineId, it.customSearchTemplate) } ?: SearchEngines.all.first().template

    fun submitAddress(raw: String) {
        val s = settings.value ?: return
        when (val r = UrlParser.resolve(raw, searchTemplate(), s.httpsFirst)) {
            is Resolved.Navigate -> loadUrl(r.url, r.httpFallback)
            is Resolved.Search -> loadUrl(r.url, null)
            is Resolved.Internal -> openCommand(r.command)
            is Resolved.Blocked -> toast(r.reason)
        }
    }

    private fun openCommand(cmd: String) = when (cmd) {
        "settings" -> openScreen(Screen.SETTINGS)
        "history" -> openScreen(Screen.HISTORY)
        "bookmarks" -> openScreen(Screen.BOOKMARKS)
        "downloads" -> openScreen(Screen.DOWNLOADS)
        "tabs" -> openScreen(Screen.TABS)
        "about" -> openScreen(Screen.ABOUT)
        "home" -> goHome()
        else -> toast("Unknown command: wyrix:$cmd")
    }

    fun loadUrl(url: String, httpFallback: String? = null, tabId: String? = null) {
        val id = tabId ?: _tabs.value.selectedId ?: run { newTab(); _tabs.value.selectedId } ?: return
        val t = tab(id) ?: return
        val host = UrlTools.hostOf(url)
        val desktop = host != null && settings.value?.desktopHosts?.contains(host) == true
        val updated = t.copy(url = url, showHome = false, error = null, httpFallback = httpFallback, desktopMode = desktop,
            crashed = false, loading = true, progress = 5, lastAccessed = now())
        _tabs.update { it.withUpdate(id) { _ -> updated } }
        val ts = sm.navigate(updated, url)
        if (desktop != t.desktopMode) ts.setDesktopMode(desktop)
        if (_tabs.value.selectedId == id) syncSession()
    }

    fun openInNewTab(url: String, isPrivate: Boolean = privateMode.value, select: Boolean = true) {
        if (!UrlTools.isWebUrl(url)) return
        if (!_tabs.value.canAdd()) { toast("Tab limit reached (${TabsState.MAX_TABS})"); return }
        val parent = _tabs.value.selected
        val nt = blankTab(isPrivate, if (isPrivate) workspaceId else parent?.workspaceId ?: workspaceId)
            .copy(groupId = if (!isPrivate) parent?.groupId else null, showHome = false, url = url, openerId = parent?.id)
        _tabs.update { it.withTab(nt, select) }
        sm.navigate(nt, url)
        if (select) syncSession()
    }

    fun newTab(isPrivate: Boolean = privateMode.value) {
        if (!_tabs.value.canAdd()) { toast("Tab limit reached (${TabsState.MAX_TABS})"); return }
        _tabs.update { it.withTab(blankTab(isPrivate), true) }
        syncSession()
        _screen.value = Screen.BROWSER
    }

    fun selectTab(id: String) {
        _tabs.update { it.withSelected(id, now()) }
        syncSession()
        _screen.value = Screen.BROWSER
    }

    fun closeTab(id: String) {
        val before = _tabs.value
        val closing = before.tab(id) ?: return
        cancelRequestsFor(id)
        sm.remove(id)
        recorded.removeAll { it.startsWith(id) }
        var next = before.withClosed(id)
        if (next.selected == null) next = next.withTab(blankTab(false, closing.workspaceId), true)
        _tabs.value = next
        syncSession()
    }

    fun closeOthers(id: String) {
        val keep = tab(id) ?: return
        _tabs.value.inScope(keep.isPrivate, keep.workspaceId).filter { it.id != id && !it.pinned }.forEach { closeTab(it.id) }
    }
    fun closeAllInScope(isPrivate: Boolean) {
        _tabs.value.inScope(isPrivate, workspaceId).filter { !it.pinned }.forEach { closeTab(it.id) }
    }
    fun closeAllPrivate() {
        _tabs.value.tabs.filter { it.isPrivate }.forEach { cancelRequestsFor(it.id); sm.remove(it.id) }
        var next = _tabs.value.withoutPrivate()
        if (next.selected == null) next = next.withTab(blankTab(false), true)
        _tabs.value = next; syncSession()
        toast("Private tabs closed")
    }
    fun duplicateTab(id: String) {
        val t = tab(id) ?: return
        val newId = UUID.randomUUID().toString()
        _tabs.update { it.withDuplicate(id, newId, now()) }
        tab(newId)?.let { if (it.hasContent) sm.navigate(it, it.url) }
        syncSession(); _screen.value = Screen.BROWSER
    }
    fun reopenClosed() {
        val newId = UUID.randomUUID().toString()
        if (_tabs.value.recentlyClosed.isEmpty()) { toast("No recently closed tabs"); return }
        _tabs.update { it.withReopened(newId, now()) }
        syncSession(); _screen.value = Screen.BROWSER
    }
    fun togglePin(id: String) { val t = tab(id) ?: return; _tabs.update { it.withPinned(id, !t.pinned) } }
    fun moveTab(id: String, delta: Int) { _tabs.update { it.withMoved(id, delta) } }

    fun switchMode(toPrivate: Boolean) {
        val st = _tabs.value
        val candidate = st.inScope(toPrivate, workspaceId).maxByOrNull { it.lastAccessed }
        if (candidate != null) { _tabs.update { it.withSelected(candidate.id, now()) }; syncSession() } else newTab(toPrivate)
    }

    fun setWorkspace(id: String) = viewModelScope.launch {
        store.set(Keys.WORKSPACE, id)
        val candidate = _tabs.value.inScope(false, id).maxByOrNull { it.lastAccessed }
        if (candidate != null) _tabs.update { it.withSelected(candidate.id, now()) }
        else _tabs.update { it.withTab(blankTab(false, id), true) }
        syncSession()
    }
    fun addWorkspace(name: String, color: Int) = viewModelScope.launch { val w = c.organization.newWorkspace(name, color); setWorkspace(w.id) }
    fun renameWorkspace(w: WorkspaceEntity, name: String) = viewModelScope.launch { c.organization.saveWorkspace(w.copy(name = name.trim().take(40).ifEmpty { w.name })) }
    fun deleteWorkspace(w: WorkspaceEntity) = viewModelScope.launch {
        if (workspaces.value.size <= 1) { toast("Keep at least one space"); return@launch }
        _tabs.value.tabs.filter { it.workspaceId == w.id && !it.isPrivate }.forEach { closeTab(it.id) }
        c.organization.deleteWorkspace(w.id)
        if (workspaceId == w.id) setWorkspace(workspaces.value.first { it.id != w.id }.id)
    }

    fun createGroupFor(tabId: String, name: String, color: Int) = viewModelScope.launch {
        val t = tab(tabId) ?: return@launch
        val g = c.organization.newGroup(name, color, t.workspaceId)
        _tabs.update { it.withGroup(tabId, g.id) }
    }
    fun setGroup(tabId: String, groupId: String?) { _tabs.update { it.withGroup(tabId, groupId) } }
    fun toggleGroup(g: TabGroupEntity) = viewModelScope.launch { c.organization.saveGroup(g.copy(collapsed = !g.collapsed)) }
    fun renameGroup(g: TabGroupEntity, name: String) = viewModelScope.launch { c.organization.saveGroup(g.copy(name = name.trim().take(40).ifEmpty { g.name })) }
    fun ungroup(g: TabGroupEntity) = viewModelScope.launch { _tabs.update { it.withoutGroup(g.id) }; c.organization.deleteGroup(g.id) }
    fun closeGroup(g: TabGroupEntity) = viewModelScope.launch {
        _tabs.value.tabs.filter { it.groupId == g.id }.forEach { closeTab(it.id) }
        c.organization.deleteGroup(g.id)
    }
    fun bookmarkGroup(g: TabGroupEntity) = viewModelScope.launch {
        val n = c.bookmarks.addAll(_tabs.value.tabs.filter { it.groupId == g.id && UrlTools.isWebUrl(it.url) }.map { it.title to it.url }, g.workspaceId)
        toast("Bookmarked $n tabs")
    }
    fun bookmarkAllTabs() = viewModelScope.launch {
        val n = c.bookmarks.addAll(_tabs.value.inScope(privateMode.value, workspaceId).filter { UrlTools.isWebUrl(it.url) }.map { it.title to it.url }, null)
        toast("Bookmarked $n tabs")
    }

    // ---- page actions ------------------------------------------------------------------------
    private fun selectedSession(): TabSession? = _tabs.value.selectedId?.let { sm.peek(it) }
    fun goBack() { selectedSession()?.goBack() }
    fun goForward() { selectedSession()?.goForward() }
    fun reload() {
        val t = _tabs.value.selected ?: return
        if (t.crashed || (sm.peek(t.id) == null && t.hasContent)) { loadUrl(t.url, t.httpFallback, t.id); return }
        update(t.id) { it.copy(error = null) }
        selectedSession()?.reload()
    }
    fun hardReload() { selectedSession()?.reloadBypassingCache() }
    fun stop() { selectedSession()?.stop() }
    fun goHome() { _tabs.value.selectedId?.let { id -> update(id) { it.copy(showHome = true) } } }
    fun tryHttpFallback() { _tabs.value.selected?.error?.httpFallback?.let { loadUrl(it, null) } }
    fun exitFullscreen() { selectedSession()?.exitFullScreen(); _fullscreen.value = false }

    /** Back-press chain; returns true when consumed. */
    fun handleBack(): Boolean {
        if (_screen.value != Screen.BROWSER) { closeScreen(); return true }
        if (_fullscreen.value) { exitFullscreen(); return true }
        val t = _tabs.value.selected ?: return false
        val live = sm.peek(t.id)
        if (t.showHome && live != null && t.hasContent) { update(t.id) { it.copy(showHome = false) }; return true }
        if (live != null && t.canGoBack && !t.showHome) { live.goBack(); return true }
        val opener = t.openerId?.let { tab(it) }
        if (opener != null) { closeTab(t.id); return true }
        return false
    }

    fun toggleDesktop() {
        val t = _tabs.value.selected ?: return
        val on = !t.desktopMode
        update(t.id) { it.copy(desktopMode = on) }
        selectedSession()?.let { it.setDesktopMode(on); it.reload() }
        UrlTools.hostOf(t.url)?.let { h -> viewModelScope.launch { store.updateDesktopHost(h, on) } }
    }
    fun toggleTrackingForTab() {
        val t = _tabs.value.selected ?: return
        val off = !t.trackingOff
        update(t.id) { it.copy(trackingOff = off) }
        selectedSession()?.let { it.setTrackingProtection(!off && settings.value?.protection != ProtectionLevel.OFF); it.reload() }
    }
    suspend fun siteException(): Boolean = selectedSession()?.let { c.engine.isSiteException(it.session) } ?: false
    fun setSiteException(on: Boolean) { selectedSession()?.let { c.engine.setSiteException(it.session, on); it.reload() } }
    fun resetPermission(p: SitePermissionInfo) {
        c.engine.resetPermission(p.raw)
        _tabs.value.selectedId?.let { id -> _sitePerms.update { m -> m + (id to m[id].orEmpty().filter { it !== p }) } }
    }
    fun clearSiteData() = viewModelScope.launch {
        UrlTools.hostOf(_tabs.value.selected?.url.orEmpty())?.let { c.engine.clearSiteData(it); toast("Cleared data for $it") }
    }

    // ---- bookmarks ---------------------------------------------------------------------------
    suspend fun bookmarkFor(url: String) = c.bookmarks.find(url)
    fun toggleBookmark() = viewModelScope.launch {
        val t = _tabs.value.selected ?: return@launch
        if (!UrlTools.isWebUrl(t.url)) { toast("Nothing to bookmark yet"); return@launch }
        val existing = c.bookmarks.find(t.url)
        if (existing != null) { c.bookmarks.delete(existing); toast("Bookmark removed") }
        else when (c.bookmarks.add(t.title, t.url, null, t.workspaceId)) {
            is AddResult.Added -> toast("Bookmarked")
            else -> toast("Couldn't bookmark this page")
        }
    }
    fun addShortcut(title: String, url: String, color: Int) = viewModelScope.launch {
        if (!UrlTools.isWebUrl(url)) toast("Enter a full web address") else c.organization.addShortcut(title, url, color)
    }
    fun addCurrentAsShortcut() { _tabs.value.selected?.let { addShortcut(it.title, it.url, 0xFF5B8CFF.toInt()) } }
    fun updateShortcut(s: ShortcutEntity) = viewModelScope.launch { c.organization.updateShortcut(s) }
    fun deleteShortcut(s: ShortcutEntity) = viewModelScope.launch { c.organization.deleteShortcut(s) }
    fun moveShortcut(id: Long, d: Int) = viewModelScope.launch { c.organization.moveShortcut(shortcuts.value, id, d) }

    val bookmarkRepo get() = c.bookmarks
    val historyRepo get() = c.history
    val downloadCoordinator get() = c.downloads

    fun importBookmarks(context: Context, uri: Uri) = viewModelScope.launch {
        val text = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { s ->
                    val buf = java.io.ByteArrayOutputStream(); val chunk = ByteArray(16 * 1024); var total = 0
                    while (true) { val n = s.read(chunk); if (n < 0) break; total += n; if (total > BookmarkHtml.MAX_BYTES) return@runCatching null; buf.write(chunk, 0, n) }
                    buf.toString(Charsets.UTF_8.name())
                }
            }.getOrNull()
        }
        if (text == null) { toast("Couldn't read that file (max 5 MB)"); return@launch }
        val r = c.bookmarks.importParsed(BookmarkHtml.parse(text))
        toast("Imported ${r.imported}, ${r.duplicates} duplicates, ${r.skipped} skipped" + if (r.truncated) " (limit reached)" else "")
    }
    fun exportBookmarks(context: Context, uri: Uri) = viewModelScope.launch {
        val html = c.bookmarks.exportHtml()
        val ok = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(html.toByteArray(Charsets.UTF_8)) } != null }.getOrDefault(false) }
        toast(if (ok) "Bookmarks exported" else "Export failed")
    }

    // ---- data clearing -----------------------------------------------------------------------
    fun clearHistory(sinceMillis: Long) = viewModelScope.launch { c.history.clearSince(sinceMillis); recorded.clear(); toast("History cleared") }
    fun clearCookies() = viewModelScope.launch { c.engine.clearCookiesAndSiteData(); toast("Cookies and site data cleared") }
    fun clearCache() = viewModelScope.launch { c.engine.clearCache(); toast("Cache cleared") }

    // ---- suggestions -------------------------------------------------------------------------
    suspend fun suggest(q: String): List<Suggestion> {
        val query = q.trim()
        if (query.isEmpty()) return emptyList()
        val out = ArrayList<Suggestion>()
        out += listOf("settings", "history", "bookmarks", "downloads", "tabs", "about").filter { it.startsWith(query.lowercase().removePrefix("wyrix:")) && query.length >= 2 }
            .map { Suggestion(Suggestion.Kind.COMMAND, it.replaceFirstChar { c -> c.uppercase() }, "wyrix:$it", "wyrix:$it") }
        out += _tabs.value.tabs.filter { !it.isPrivate && (it.title.contains(query, true) || it.url.contains(query, true)) }.take(3)
            .map { Suggestion(Suggestion.Kind.TAB, it.title.ifBlank { it.url }, "Switch to tab", it.url, it.id) }
        out += c.bookmarks.search(query, 4).map { Suggestion(Suggestion.Kind.BOOKMARK, it.title, it.url, it.url) }
        out += c.history.suggest(query, 4).map { Suggestion(Suggestion.Kind.HISTORY, it.title.ifBlank { it.url }, it.url, it.url) }
        return out.distinctBy { it.action }.take(8)
    }

    suspend fun remoteSuggest(q: String): List<Suggestion> {
        val s = settings.value ?: return emptyList()
        if (!s.remoteSuggestions || privateMode.value) return emptyList()
        val tmpl = SearchEngines.byId(s.searchEngineId)?.suggestUrl ?: return emptyList()
        return withContext(Dispatchers.IO) { RemoteSuggestions.fetch(tmpl, q) }.map { Suggestion(Suggestion.Kind.REMOTE, it, "Suggestion", it) }
    }

    // ---- screens / settings ------------------------------------------------------------------
    fun openScreen(s: Screen) { _screen.value = s }
    fun closeScreen() { _screen.value = if (_screen.value == Screen.ABOUT) Screen.SETTINGS else Screen.BROWSER }
    fun <T> setPref(key: Preferences.Key<T>, value: T) { viewModelScope.launch { store.set(key, value) } }
    fun resetAppearance() { viewModelScope.launch { store.remove(*Keys.APPEARANCE.toTypedArray()) } }
    fun finishOnboarding() { setPref(Keys.ONBOARDING, true) }
    fun setAndroidPermissionRequester(r: ((Array<String>, (Boolean) -> Unit) -> Unit)?) { sm.androidPermissionRequester = r }

    fun handleIntent(action: String?, data: String?, text: String?) {
        when (action) {
            android.content.Intent.ACTION_VIEW -> if (data != null && UrlTools.isWebUrl(data)) {
                if (settings.value?.openLinksInNewTab == true || _tabs.value.selected?.hasContent == true) openInNewTab(data, false) else loadUrl(data)
            }
            android.content.Intent.ACTION_SEND -> if (!text.isNullOrBlank()) {
                when (val r = UrlParser.resolve(text, searchTemplate(), settings.value?.httpsFirst != false)) {
                    is Resolved.Navigate -> openInNewTab(r.url, false)
                    is Resolved.Search -> openInNewTab(r.url, false)
                    else -> toast("Can't open the shared text")
                }
            }
        }
    }

    // ---- lifecycle ---------------------------------------------------------------------------
    private fun flushBlocked() { val n = blockedUnflushed; blockedUnflushed = 0; if (n > 0) viewModelScope.launch { store.addBlocked(n.toLong()) } }

    fun persistNow() {
        flushBlocked()
        val st = _tabs.value
        val normal = st.tabs.filter { !it.isPrivate }
        val states = sm.statesFor(normal.filter { it.hasContent }.map { it.id })
        c.appScope.launch {
            c.tabRepository.save(normal, states)
            store.setSelectedTab(st.selected?.takeIf { !it.isPrivate }?.id)
        }
    }

    fun onTrimMemory(critical: Boolean) { sm.trim(_tabs.value.selectedId, if (critical) 1 else 4) }

    override fun onCleared() { persistNow() }

    class Factory(private val app: WyrixApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = BrowserViewModel(app) as T
    }
}
