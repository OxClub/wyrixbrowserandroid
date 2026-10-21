package com.wyrix.browser.ui.library

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wyrix.browser.BrowserViewModel
import com.wyrix.browser.data.DownloadEntity
import com.wyrix.browser.data.DownloadOutcome
import com.wyrix.browser.data.DownloadStatus
import com.wyrix.browser.ui.common.*
import com.wyrix.browser.ui.browser.copyText
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Row
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(vm: BrowserViewModel) {
    val items by vm.downloads.collectAsStateWithLifecycle()
    val coordinator = vm.downloadCoordinator
    val scope = rememberCoroutineScope()
    var filter by remember { mutableIntStateOf(0) }
    val shown = when (filter) {
        1 -> items.filter { it.status in DownloadStatus.ACTIVE }
        2 -> items.filter { it.status == DownloadStatus.SUCCESS }
        3 -> items.filter { it.status == DownloadStatus.FAILED }
        else -> items
    }
    ScreenScaffold("Downloads", onBack = { vm.closeScreen() }, actions = {
        TextButton(onClick = { scope.launch { coordinator.clearFinished() } }) { Text("Clear finished") }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Active", "Done", "Failed").forEachIndexed { i, label ->
                    FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(label) })
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.id }) { d -> DownloadRow(d, vm) }
                if (shown.isEmpty()) item { EmptyState(if (items.isEmpty()) "No downloads" else "Nothing here", "Files you download in normal tabs appear here. Downloads from private tabs are saved but not listed.") }
            }
        }
    }
}

@Composable
private fun DownloadRow(d: DownloadEntity, vm: BrowserViewModel) {
    val ctx = LocalContext.current
    val c = vm.downloadCoordinator
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val progress = if (d.totalBytes > 0) (d.downloadedBytes.toFloat() / d.totalBytes).coerceIn(0f, 1f) else null
    ListItem(
        headlineContent = { Text(d.fileName, maxLines = 1) },
        supportingContent = {
            Column {
                Text(when (d.status) {
                    DownloadStatus.SUCCESS -> "Done · ${formatBytes(d.totalBytes)}"
                    DownloadStatus.FAILED -> "Failed: ${d.reason ?: "unknown error"}"
                    DownloadStatus.PAUSED -> "Paused"
                    DownloadStatus.PENDING -> "Waiting…"
                    else -> "${formatBytes(d.downloadedBytes)} of ${formatBytes(d.totalBytes)}"
                } + " · " + DateUtils.getRelativeTimeSpanString(d.startedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS), color = if (d.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                if (d.status in DownloadStatus.ACTIVE || (d.status == DownloadStatus.RUNNING)) {
                    if (progress != null) LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().padding(top = 4.dp)) else LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Options for ${d.fileName}") }
                DropdownMenu(menu, { menu = false }) {
                    if (d.status == DownloadStatus.SUCCESS) {
                        DropdownMenuItem({ Text("Open") }, { menu = false
                            c.openIntent(d)?.let { try { ctx.startActivity(it) } catch (e: ActivityNotFoundException) { Toast.makeText(ctx, "No app can open this file", Toast.LENGTH_SHORT).show() } } })
                        DropdownMenuItem({ Text("Share") }, { menu = false; c.shareIntent(d)?.let { ctx.startActivity(it) } })
                    }
                    if (d.status == DownloadStatus.FAILED) DropdownMenuItem({ Text("Retry") }, { menu = false
                        scope.launch { val r = c.retry(d); if (r is DownloadOutcome.Unsupported) vm.toast(r.reason) } })
                    if (d.status in DownloadStatus.ACTIVE) DropdownMenuItem({ Text("Cancel download") }, { menu = false; scope.launch { c.remove(d, true) } })
                    if (d.url.startsWith("http")) DropdownMenuItem({ Text("Copy link") }, { menu = false; copyText(ctx, d.url); vm.toast("Link copied") })
                    DropdownMenuItem({ Text("Remove from list") }, { menu = false; scope.launch { c.remove(d, false) } })
                    DropdownMenuItem({ Text("Delete file") }, { menu = false; scope.launch { c.remove(d, true) } })
                }
            }
        },
    )
}
