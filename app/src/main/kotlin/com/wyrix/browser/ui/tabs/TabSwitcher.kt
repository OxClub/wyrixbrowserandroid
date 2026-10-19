package com.wyrix.browser.ui.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wyrix.browser.BrowserViewModel
import com.wyrix.browser.core.UrlTools
import com.wyrix.browser.data.Keys
import com.wyrix.browser.data.TabGroupEntity
import com.wyrix.browser.data.TabLayoutMode
import com.wyrix.browser.data.WorkspaceEntity
import com.wyrix.browser.tabs.TabState
import com.wyrix.browser.ui.common.*

private sealed interface Row2 {
    data class Header(val group: TabGroupEntity, val count: Int) : Row2
    data class Item(val tab: TabState) : Row2
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TabSwitcher(vm: BrowserViewModel) {
    val state by vm.tabs.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val workspaces by vm.workspaces.collectAsStateWithLifecycle()
    val privateMode by vm.privateMode.collectAsStateWithLifecycle()
    val s = settings ?: return
    var query by remember { mutableStateOf("") }
    var galaxy by remember { mutableStateOf(false) }
    var addWorkspace by remember { mutableStateOf(false) }
    var groupTarget by remember { mutableStateOf<TabState?>(null) }
    var renameGroup by remember { mutableStateOf<TabGroupEntity?>(null) }
    var wsMenu by remember { mutableStateOf<WorkspaceEntity?>(null) }
    var confirmCloseAll by remember { mutableStateOf(false) }

    val scopeTabs = state.inScope(privateMode, s.currentWorkspaceId)
        .filter { query.isBlank() || it.title.contains(query, true) || it.url.contains(query, true) }
    val groupMap = groups.filter { it.workspaceId == s.currentWorkspaceId }.associateBy { it.id }
    val rows = buildList {
        val pinned = scopeTabs.filter { it.pinned }
        pinned.forEach { add(Row2.Item(it)) }
        val rest = scopeTabs.filter { !it.pinned }
        groupMap.values.forEach { g ->
            val inG = rest.filter { it.groupId == g.id }
            if (inG.isNotEmpty() || query.isBlank()) { add(Row2.Header(g, inG.size)); if (!g.collapsed) inG.forEach { add(Row2.Item(it)) } }
        }
        rest.filter { it.groupId == null || it.groupId !in groupMap }.forEach { add(Row2.Item(it)) }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.closeScreen() }) { Icon(Icons.Filled.Close, "Close tab switcher") }
                FilterChip(selected = !privateMode, onClick = { vm.switchMode(false) }, label = { Text("Tabs") })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = privateMode, onClick = { vm.switchMode(true) }, label = { Text("Private") })
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { galaxy = !galaxy }) { Icon(Icons.Filled.Favorite, if (galaxy) "Show tab grid" else "Show Tab Galaxy") }
                IconButton(onClick = { vm.reopenClosed() }, enabled = state.recentlyClosed.isNotEmpty()) { Icon(Icons.Filled.Refresh, "Reopen closed tab") }
                IconButton(onClick = { vm.newTab(privateMode) }) { Icon(Icons.Filled.Add, "New tab") }
            }
            OutlinedTextField(query, { query = it.take(100) }, singleLine = true, placeholder = { Text("Search tabs") },
                leadingIcon = { Icon(Icons.Filled.Search, null) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            if (!privateMode) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    workspaces.forEach { w ->
                        FilterChip(selected = w.id == s.currentWorkspaceId, onClick = { vm.setWorkspace(w.id) },
                            label = { Text(w.name) }, leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(Color(w.color))) },
                            modifier = Modifier.combinedClickable(onClick = { vm.setWorkspace(w.id) }, onLongClick = { wsMenu = w }, onLongClickLabel = "Space options"))
                    }
                    AssistChip(onClick = { addWorkspace = true }, label = { Text("New space") }, leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)) })
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${scopeTabs.size} tabs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.setPref(Keys.TAB_LAYOUT, when (s.tabLayout) { TabLayoutMode.GRID -> TabLayoutMode.LIST.name; TabLayoutMode.LIST -> TabLayoutMode.COMPACT.name; TabLayoutMode.COMPACT -> TabLayoutMode.GRID.name }) }) { Text("View: ${s.tabLayout.name.lowercase()}") }
                TextButton(onClick = { confirmCloseAll = true }, enabled = scopeTabs.any { !it.pinned }) { Text("Close all") }
            }

            if (galaxy) GalaxyView(groupMap.values.toList(), rows.filterIsInstance<Row2.Item>().map { it.tab }, onTab = { vm.selectTab(it) }, onGroup = { renameGroup = it })
            else if (scopeTabs.isEmpty()) EmptyState(if (privateMode) "No private tabs" else "No tabs here", "Tap + to open a new tab.")
            else LazyVerticalGrid(
                columns = if (s.tabLayout == TabLayoutMode.GRID) GridCells.Adaptive(160.dp) else GridCells.Fixed(1),
                contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(rows, key = { r -> when (r) { is Row2.Header -> "g" + r.group.id; is Row2.Item -> r.tab.id } }, span = { r -> if (r is Row2.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) }) { r ->
                    when (r) {
                        is Row2.Header -> GroupHeader(r.group, r.count, vm, onRename = { renameGroup = r.group })
                        is Row2.Item -> TabCard(r.tab, r.tab.id == state.selectedId, s.tabLayout, groupMap[r.tab.groupId], vm, onGroup = { groupTarget = r.tab })
                    }
                }
            }
        }
    }

    if (addWorkspace) TextInputDialog("New space", label = "Name", onConfirm = { vm.addWorkspace(it, PaletteColors.random()); addWorkspace = false }, onDismiss = { addWorkspace = false })
    wsMenu?.let { w ->
        var rename by remember { mutableStateOf(false) }
        if (rename) TextInputDialog("Rename space", w.name, "Name", onConfirm = { vm.renameWorkspace(w, it); wsMenu = null }, onDismiss = { wsMenu = null })
        else AlertDialog(onDismissRequest = { wsMenu = null }, title = { Text(w.name) },
            text = { Column { TextButton(onClick = { rename = true }) { Text("Rename") }; TextButton(onClick = { vm.deleteWorkspace(w); wsMenu = null }) { Text("Delete space and close its tabs", color = MaterialTheme.colorScheme.error) } } },
            confirmButton = { TextButton(onClick = { wsMenu = null }) { Text("Close") } })
    }
    if (confirmCloseAll) ConfirmDialog("Close all tabs?", "Pinned tabs stay open.", "Close all", { vm.closeAllInScope(privateMode); confirmCloseAll = false }, { confirmCloseAll = false })
    groupTarget?.let { t -> GroupPicker(t, groups.filter { it.workspaceId == t.workspaceId }, vm) { groupTarget = null } }
    renameGroup?.let { g -> TextInputDialog("Rename group", g.name, "Name", onConfirm = { vm.renameGroup(g, it); renameGroup = null }, onDismiss = { renameGroup = null }) }
}

@Composable
private fun GroupHeader(g: TabGroupEntity, count: Int, vm: BrowserViewModel, onRename: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { vm.toggleGroup(g) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(g.color)))
        Spacer(Modifier.width(8.dp))
        Text("${g.name} ($count)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Icon(if (g.collapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp, if (g.collapsed) "Expand group" else "Collapse group")
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Group options") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("Rename") }, { menu = false; onRename() })
                DropdownMenuItem({ Text("Bookmark all") }, { menu = false; vm.bookmarkGroup(g) })
                DropdownMenuItem({ Text("Ungroup") }, { menu = false; vm.ungroup(g) })
                DropdownMenuItem({ Text("Close group") }, { menu = false; vm.closeGroup(g) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabCard(tab: TabState, selected: Boolean, layout: TabLayoutMode, group: TabGroupEntity?, vm: BrowserViewModel, onGroup: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val host = UrlTools.hostOf(tab.url)
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val compact = layout == TabLayoutMode.COMPACT
    Surface(
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(16.dp))
            .combinedClickable(onClick = { vm.selectTab(tab.id) }, onLongClick = { menu = true }, onLongClickLabel = "Tab options")
            .semantics { contentDescription = "${tab.title.ifBlank { host ?: "New tab" }}${if (tab.pinned) ", pinned" else ""}${if (selected) ", current tab" else ""}" },
    ) {
        Row(Modifier.padding(if (compact) 6.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SiteTile(host, if (compact) 28.dp else 40.dp, group?.let { Color(it.color) })
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(tab.title.ifBlank { host ?: "New tab" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                if (!compact) Text(host ?: "Home", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (tab.pinned) Icon(Icons.Filled.Star, "Pinned", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.MoreVert, "Tab options") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(if (tab.pinned) "Unpin" else "Pin") }, { menu = false; vm.togglePin(tab.id) })
                    DropdownMenuItem({ Text("Duplicate") }, { menu = false; vm.duplicateTab(tab.id) })
                    if (!tab.isPrivate) DropdownMenuItem({ Text("Group…") }, { menu = false; onGroup() })
                    DropdownMenuItem({ Text("Move earlier") }, { menu = false; vm.moveTab(tab.id, -1) })
                    DropdownMenuItem({ Text("Move later") }, { menu = false; vm.moveTab(tab.id, 1) })
                    DropdownMenuItem({ Text("Close other tabs") }, { menu = false; vm.closeOthers(tab.id) })
                }
            }
            IconButton(onClick = { vm.closeTab(tab.id) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Close, "Close tab ${tab.title.ifBlank { host ?: "" }}") }
        }
    }
}

@Composable
private fun GroupPicker(tab: TabState, groups: List<TabGroupEntity>, vm: BrowserViewModel, onDismiss: () -> Unit) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(PaletteColors.first()) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!creating) {
                    if (tab.groupId != null) TextButton(onClick = { vm.setGroup(tab.id, null); onDismiss() }) { Text("Remove from group") }
                    groups.forEach { g -> TextButton(onClick = { vm.setGroup(tab.id, g.id); onDismiss() }) { Box(Modifier.size(10.dp).clip(CircleShape).background(Color(g.color))); Spacer(Modifier.width(8.dp)); Text(g.name) } }
                    TextButton(onClick = { creating = true }) { Text("New group…") }
                } else {
                    OutlinedTextField(name, { name = it.take(40) }, label = { Text("Group name") }, singleLine = true)
                    ColorRow(color) { color = it }
                }
            }
        },
        confirmButton = { if (creating) TextButton(onClick = { vm.createGroupFor(tab.id, name, color); onDismiss() }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Lightweight 2D "constellation" overview: groups as planets sized by tab count. No GPU-heavy rendering. */
@Composable
private fun GalaxyView(groups: List<TabGroupEntity>, tabs: List<TabState>, onTab: (String) -> Unit, onGroup: (TabGroupEntity) -> Unit) {
    val ungrouped = tabs.filter { it.groupId == null || groups.none { g -> g.id == it.groupId } }
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(groups.size) { i ->
            val g = groups[i]; val members = tabs.filter { it.groupId == g.id }
            Surface(shape = RoundedCornerShape(28.dp), color = Color(g.color).copy(alpha = 0.14f), modifier = Modifier.fillMaxWidth().clickable { onGroup(g) }.semantics { contentDescription = "Group ${g.name}, ${members.size} tabs" }) {
                Column(Modifier.padding(16.dp)) {
                    Text("${g.name} · ${members.size}", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) { members.take(8).forEach { t -> SiteTile(UrlTools.hostOf(t.url), 32.dp, Color(g.color), Modifier.clickable { onTab(t.id) }) } }
                }
            }
        }
        item {
            Text("Ungrouped · ${ungrouped.size}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ungrouped.take(10).forEach { t -> SiteTile(UrlTools.hostOf(t.url), 36.dp, modifier = Modifier.clickable { onTab(t.id) }) } }
        }
    }
}
