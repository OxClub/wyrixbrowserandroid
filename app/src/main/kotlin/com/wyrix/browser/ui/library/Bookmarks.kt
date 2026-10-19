package com.wyrix.browser.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wyrix.browser.BrowserViewModel
import com.wyrix.browser.core.UrlTools
import com.wyrix.browser.data.BookmarkEntity
import com.wyrix.browser.data.FolderEntity
import com.wyrix.browser.ui.common.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(vm: BrowserViewModel) {
    val ctx = LocalContext.current
    val repo = vm.bookmarkRepo
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var folderId by remember { mutableStateOf<Long?>(null) }
    var folderName by remember { mutableStateOf("Bookmarks") }
    var parents by remember { mutableStateOf(listOf<Pair<Long?, String>>()) }
    var query by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<BookmarkEntity?>(null) }
    var renameFolder by remember { mutableStateOf<FolderEntity?>(null) }
    var deleteFolder by remember { mutableStateOf<FolderEntity?>(null) }

    val items by remember(folderId) { repo.observeIn(folderId) }.collectAsStateWithLifecycle(emptyList())
    val folders by remember(folderId) { repo.observeFolders(folderId) }.collectAsStateWithLifecycle(emptyList())
    var results by remember { mutableStateOf<List<BookmarkEntity>>(emptyList()) }
    LaunchedEffect(query) { results = repo.search(query) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) vm.importBookmarks(ctx, u) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { u -> if (u != null) vm.exportBookmarks(ctx, u) }

    ScreenScaffold(folderName, onBack = { if (parents.isNotEmpty()) { val p = parents.last(); parents = parents.dropLast(1); folderId = p.first; folderName = p.second } else vm.closeScreen() }, snackbar = snack, actions = {
        IconButton(onClick = { newFolder = true }) { Icon(Icons.Filled.Add, "New folder") }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("Import from HTML file") }, { menu = false; importer.launch(arrayOf("text/html", "text/plain", "*/*")) })
                DropdownMenuItem({ Text("Export to HTML file") }, { menu = false; exporter.launch("wyrix-bookmarks.html") })
                DropdownMenuItem({ Text("Bookmark all open tabs") }, { menu = false; vm.bookmarkAllTabs() })
            }
        }
    }) { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedTextField(query, { query = it.take(100) }, singleLine = true, placeholder = { Text("Search bookmarks") },
                leadingIcon = { Icon(Icons.Filled.Search, null) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            val showing = if (query.isNotBlank()) results else items
            LazyColumn(Modifier.fillMaxSize()) {
                if (query.isBlank()) items(folders, key = { "f" + it.id }) { f ->
                    ListItem(headlineContent = { Text(f.name) }, leadingContent = { Icon(Icons.Filled.Menu, null) },
                        trailingContent = { Row { IconButton(onClick = { renameFolder = f }) { Icon(Icons.Filled.Edit, "Rename folder ${f.name}") }; IconButton(onClick = { deleteFolder = f }) { Icon(Icons.Filled.Delete, "Delete folder ${f.name}") } } },
                        modifier = Modifier.clickable { parents = parents + (folderId to folderName); folderId = f.id; folderName = f.name })
                }
                items(showing, key = { "b" + it.id }) { b ->
                    ListItem(
                        headlineContent = { Text(b.title, maxLines = 1) }, supportingContent = { Text(UrlTools.hostOf(b.url).orEmpty(), maxLines = 1) },
                        leadingContent = { SiteTile(UrlTools.hostOf(b.url), 36.dp) },
                        trailingContent = {
                            Row {
                                if (query.isBlank()) {
                                    IconButton(onClick = { scope.launch { repo.reorder(items, b.id, -1) } }) { Icon(Icons.Filled.KeyboardArrowUp, "Move ${b.title} up") }
                                    IconButton(onClick = { scope.launch { repo.reorder(items, b.id, 1) } }) { Icon(Icons.Filled.KeyboardArrowDown, "Move ${b.title} down") }
                                }
                                IconButton(onClick = { edit = b }) { Icon(Icons.Filled.Edit, "Edit ${b.title}") }
                                IconButton(onClick = {
                                    scope.launch {
                                        repo.delete(b)
                                        if (snack.showSnackbar("Deleted", "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) repo.restore(b)
                                    }
                                }) { Icon(Icons.Filled.Delete, "Delete ${b.title}") }
                            }
                        },
                        modifier = Modifier.clickable { vm.loadUrl(b.url); vm.closeScreen() },
                    )
                }
                if (showing.isEmpty() && folders.isEmpty()) item { EmptyState("No bookmarks yet", "Tap the star in the address bar, or import a bookmarks HTML file from another browser (⋮ menu).") }
            }
        }
    }

    if (newFolder) TextInputDialog("New folder", label = "Name", onConfirm = { n -> scope.launch { repo.createFolder(n, folderId) }; newFolder = false }, onDismiss = { newFolder = false })
    renameFolder?.let { f -> TextInputDialog("Rename folder", f.name, "Name", onConfirm = { n -> scope.launch { repo.renameFolder(f, n) }; renameFolder = null }, onDismiss = { renameFolder = null }) }
    deleteFolder?.let { f -> ConfirmDialog("Delete \"${f.name}\"?", "The folder and every bookmark inside it will be deleted.", "Delete", { scope.launch { repo.deleteFolderTree(f) }; deleteFolder = null }, { deleteFolder = null }) }
    edit?.let { b -> EditBookmarkDialog(b, onSave = { t, u -> scope.launch { repo.update(b.copy(title = t.trim().ifEmpty { b.title }, url = if (UrlTools.isWebUrl(u)) u else b.url)) }; edit = null }) { edit = null } }
}

@Composable
private fun EditBookmarkDialog(b: BookmarkEntity, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(b.title) }
    var url by remember { mutableStateOf(b.url) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Edit bookmark") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it.take(300) }, label = { Text("Title") }, singleLine = true)
            OutlinedTextField(url, { url = it.take(2000) }, label = { Text("Address") }, singleLine = true, isError = !UrlTools.isWebUrl(url))
        } },
        confirmButton = { TextButton(onClick = { onSave(title, url) }, enabled = UrlTools.isWebUrl(url)) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
