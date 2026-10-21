package com.wyrix.browser.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wyrix.browser.BrowserViewModel
import com.wyrix.browser.Screen
import com.wyrix.browser.core.UrlTools
import com.wyrix.browser.data.HomeLayout
import com.wyrix.browser.data.Keys
import com.wyrix.browser.data.ShortcutEntity
import com.wyrix.browser.ui.common.*
import com.wyrix.browser.ui.theme.primary
import com.wyrix.browser.ui.theme.secondary

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(vm: BrowserViewModel, onStartEdit: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val shortcuts by vm.shortcuts.collectAsStateWithLifecycle()
    val frequent by vm.frequent.collectAsStateWithLifecycle()
    val recent by vm.recentBookmarks.collectAsStateWithLifecycle()
    val recentHistory by vm.recentHistory.collectAsStateWithLifecycle()
    val saved by vm.savedArticles.collectAsStateWithLifecycle()
    val privateMode by vm.privateMode.collectAsStateWithLifecycle()
    val blockedSession by vm.blockedSession.collectAsStateWithLifecycle()
    val st = s ?: return
    var editShortcut by remember { mutableStateOf<ShortcutEntity?>(null) }
    var addShortcut by remember { mutableStateOf(false) }
    var customize by remember { mutableStateOf(false) }
    val limit = when (st.homeLayout) { HomeLayout.COMPACT -> 4; HomeLayout.STANDARD -> 8; HomeLayout.EXPANDED -> 12 }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).background(
            Brush.verticalGradient(listOf(st.accent.primary().copy(alpha = if (privateMode) 0.22f else 0.12f), MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background)),
        ),
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 24.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WyrixEmblem(40.dp, st.accent)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (privateMode) "Private browsing" else "WYRIX", style = MaterialTheme.typography.titleLarge)
                    Text(if (privateMode) "History, cookies and site data from private tabs aren't kept." else "Fast, private, yours.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { customize = true }) { Icon(Icons.Filled.Edit, "Customize home") }
            }
            Spacer(Modifier.height(20.dp))
            Surface(Modifier.fillMaxWidth().clickable(onClickLabel = "Search or enter address") { onStartEdit() }, shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(12.dp))
                    Text("Search or enter address", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (st.showShortcuts) {
                SectionHeader("Shortcuts", Modifier.padding(horizontal = 0.dp))
                val items = shortcuts.take(limit)
                FlowRowCompat {
                    items.forEach { sc ->
                        Column(Modifier.width(76.dp).combinedClickable(onClick = { vm.loadUrl(sc.url) }, onLongClick = { editShortcut = sc }, onLongClickLabel = "Edit shortcut").padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            SiteTile(UrlTools.hostOf(sc.url), 52.dp, Color(sc.color))
                            Text(sc.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                        }
                    }
                    Column(Modifier.width(76.dp).clickable { addShortcut = true }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, "Add shortcut") }
                        Text("Add", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (st.showBookmarks && recent.isNotEmpty()) {
                SectionHeader("Recent bookmarks", Modifier.padding(horizontal = 0.dp))
                recent.take(if (st.homeLayout == HomeLayout.COMPACT) 3 else 5).forEach { b ->
                    ListItem(headlineContent = { Text(b.title, maxLines = 1) }, supportingContent = { Text(UrlTools.hostOf(b.url).orEmpty(), maxLines = 1) },
                        leadingContent = { SiteTile(UrlTools.hostOf(b.url), 36.dp) }, modifier = Modifier.clickable { vm.loadUrl(b.url) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                }
            }
            if (st.showRecent && !privateMode && recentHistory.isNotEmpty()) {
                SectionHeader("Recently visited", Modifier.padding(horizontal = 0.dp))
                recentHistory.take(if (st.homeLayout == HomeLayout.COMPACT) 3 else 5).forEach { h ->
                    ListItem(headlineContent = { Text(h.title.ifBlank { h.host }, maxLines = 1) }, supportingContent = { Text(h.host, maxLines = 1) },
                        leadingContent = { SiteTile(h.host, 36.dp) }, modifier = Modifier.clickable { vm.loadUrl(h.url) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                }
            }
            if (st.showSaved && saved.isNotEmpty()) {
                SectionHeader("Saved articles", Modifier.padding(horizontal = 0.dp))
                saved.take(3).forEach { m ->
                    ListItem(headlineContent = { Text(m.title.ifBlank { m.url }, maxLines = 1) }, supportingContent = { Text(m.siteName, maxLines = 1) },
                        leadingContent = { Icon(WyrixIcons.Reader, null) }, modifier = Modifier.clickable { vm.openSavedArticle(m.id) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                }
                TextButton(onClick = { vm.openSavedList() }) { Text("See all saved articles") }
            }
            if (st.showFrequent && !privateMode && frequent.isNotEmpty()) {
                SectionHeader("Frequently visited", Modifier.padding(horizontal = 0.dp))
                frequent.take(if (st.homeLayout == HomeLayout.COMPACT) 3 else 5).forEach { h ->
                    ListItem(headlineContent = { Text(h.title.ifBlank { h.host }, maxLines = 1) }, supportingContent = { Text(h.host, maxLines = 1) },
                        leadingContent = { SiteTile(h.host, 36.dp) }, modifier = Modifier.clickable { vm.loadUrl(h.url) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                }
            }
            if (st.showPrivacyStats) {
                Spacer(Modifier.height(16.dp))
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Privacy at a glance", style = MaterialTheme.typography.titleMedium)
                        Text("$blockedSession trackers blocked this session · ${st.blockedTotal + blockedSession} since install", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Counts come from the engine's blocking events; they are local and never uploaded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (addShortcut) ShortcutDialog(null, { t, u, c -> vm.addShortcut(t, u, c); addShortcut = false }, null, null, null) { addShortcut = false }
    editShortcut?.let { sc ->
        ShortcutDialog(sc, { t, u, c -> vm.updateShortcut(sc.copy(title = t, url = u, color = c)); editShortcut = null },
            { vm.deleteShortcut(sc); editShortcut = null }, { vm.moveShortcut(sc.id, -1); editShortcut = null }, { vm.moveShortcut(sc.id, 1); editShortcut = null }) { editShortcut = null }
    }
    if (customize) CustomizeDialog(vm) { customize = false }
}

/** Simple wrapping row built on a Column of Rows (avoids experimental FlowRow API). */
@Composable
private fun FlowRowCompat(content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        var x = 0; var y = 0; var rowH = 0
        val pos = ArrayList<Pair<Int, Int>>()
        for (p in placeables) {
            if (x + p.width > constraints.maxWidth && x > 0) { x = 0; y += rowH; rowH = 0 }
            pos += x to y; x += p.width; rowH = maxOf(rowH, p.height)
        }
        layout(constraints.maxWidth, y + rowH) { placeables.forEachIndexed { i, p -> p.placeRelative(pos[i].first, pos[i].second) } }
    }
}

@Composable
private fun ShortcutDialog(initial: ShortcutEntity?, onSave: (String, String, Int) -> Unit, onDelete: (() -> Unit)?, onLeft: (() -> Unit)?, onRight: (() -> Unit)?, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "https://") }
    var color by remember { mutableStateOf(initial?.color ?: PaletteColors.first()) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if (initial == null) "Add shortcut" else "Edit shortcut") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it.take(40) }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(url, { url = it.take(2000) }, label = { Text("Address") }, singleLine = true)
                ColorRow(color) { color = it }
                if (onLeft != null && onRight != null) Row { TextButton(onClick = onLeft) { Text("Move earlier") }; TextButton(onClick = onRight) { Text("Move later") } }
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete shortcut", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, url, color) }, enabled = url.length > 8) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CustomizeDialog(vm: BrowserViewModel, onDismiss: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val st = s ?: return
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Customize home") },
        text = {
            Column {
                SettingSwitch("Shortcuts", checked = st.showShortcuts) { vm.setPref(Keys.SHOW_SHORTCUTS, it) }
                SettingSwitch("Recent bookmarks", checked = st.showBookmarks) { vm.setPref(Keys.SHOW_BOOKMARKS, it) }
                SettingSwitch("Recently visited", checked = st.showRecent) { vm.setPref(Keys.SHOW_RECENT, it) }
                SettingSwitch("Saved articles", checked = st.showSaved) { vm.setPref(Keys.SHOW_SAVED, it) }
                SettingSwitch("Frequently visited", checked = st.showFrequent) { vm.setPref(Keys.SHOW_FREQUENT, it) }
                SettingSwitch("Privacy summary", checked = st.showPrivacyStats) { vm.setPref(Keys.SHOW_STATS, it) }
                TextButton(onClick = { vm.openScreen(Screen.SETTINGS); onDismiss() }) { Text("More appearance options in Settings") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
