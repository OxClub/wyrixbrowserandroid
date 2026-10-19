package com.wyrix.browser.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wyrix.browser.BrowserViewModel
import com.wyrix.browser.core.groupByDay
import com.wyrix.browser.data.HistoryEntity
import com.wyrix.browser.ui.common.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: BrowserViewModel) {
    val repo = vm.historyRepo
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var clearDialog by remember { mutableStateOf(false) }
    val items by remember(query) { repo.observe(query) }.collectAsStateWithLifecycle(emptyList())
    val groups = remember(items) { groupByDay(items) { it.lastVisit } }
    val today = LocalDate.now()
    val fmt = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    ScreenScaffold("History", onBack = { vm.closeScreen() }, snackbar = snack, actions = {
        IconButton(onClick = { clearDialog = true }) { Icon(Icons.Filled.Delete, "Clear history") }
    }) { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedTextField(query, { query = it.take(100) }, singleLine = true, placeholder = { Text("Search history") },
                leadingIcon = { Icon(Icons.Filled.Search, null) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                groups.forEach { (day, list) ->
                    item(key = "d$day") {
                        SectionHeader(when (day) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> day.format(fmt) })
                    }
                    items(list, key = { it.id }) { h -> HistoryRow(h, vm, snack) }
                }
                if (items.isEmpty()) item { EmptyState(if (query.isBlank()) "No history" else "No matches", "Pages you visit in normal tabs appear here. Private tabs never do.") }
            }
        }
    }
    if (clearDialog) ClearDialog(onDismiss = { clearDialog = false }) { since -> vm.clearHistory(since); clearDialog = false }
}

@Composable
private fun HistoryRow(h: HistoryEntity, vm: BrowserViewModel, snack: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(h.title.ifBlank { h.host }, maxLines = 1) }, supportingContent = { Text(h.host, maxLines = 1) },
        leadingContent = { SiteTile(h.host, 36.dp) },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Options for ${h.host}") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Delete") }, { menu = false; scope.launch {
                        vm.historyRepo.delete(h)
                        if (snack.showSnackbar("Removed", "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.historyRepo.restore(h)
                    } })
                    DropdownMenuItem({ Text("Delete all from ${h.host}") }, { menu = false; scope.launch { vm.historyRepo.deleteHost(h.host) } })
                }
            }
        },
        modifier = Modifier.clickable { vm.loadUrl(h.url); vm.closeScreen() },
    )
}

@Composable
fun ClearDialog(onDismiss: () -> Unit, onClear: (Long) -> Unit) {
    val now = System.currentTimeMillis()
    val options = listOf("Last hour" to now - 3_600_000L, "Last 24 hours" to now - 86_400_000L, "Last 7 days" to now - 7 * 86_400_000L, "All time" to 0L)
    var sel by remember { mutableStateOf(0) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Clear history") },
        text = { Column { options.forEachIndexed { i, (label, _) ->
            Row(Modifier.fillMaxWidth().clickable { sel = i }.padding(vertical = 8.dp)) { RadioButton(sel == i, { sel = i }); Spacer(Modifier.width(8.dp)); Text(label) } } } },
        confirmButton = { TextButton(onClick = { onClear(options[sel].second) }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
