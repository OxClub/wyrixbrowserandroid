package com.wyrix.browser.ui.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wyrix.browser.BrowserViewModel
import com.wyrix.browser.Suggestion
import com.wyrix.browser.core.UrlTools
import com.wyrix.browser.data.ProtectionLevel
import com.wyrix.browser.engine.ContextMenuInfo
import com.wyrix.browser.engine.PromptRequest
import com.wyrix.browser.tabs.ErrorKind
import com.wyrix.browser.tabs.TabState
import com.wyrix.browser.ui.common.ConfirmDialog
import com.wyrix.browser.ui.common.SiteTile

@Composable
fun ErrorOverlay(tab: TabState, vm: BrowserViewModel) {
    val err = tab.error
    val (title, body) = when {
        tab.crashed -> "This page stopped working" to "The page's process ended unexpectedly. Reloading starts a fresh one."
        err?.kind == ErrorKind.SECURITY -> "Connection isn't secure" to "WYRIX couldn't verify this site's security certificate, so it didn't load the page. This protects you from impersonation and eavesdropping."
        err?.kind == ErrorKind.NETWORK -> "Can't reach this site" to "Check your connection, then try again. The site may also be down."
        err?.kind == ErrorKind.UNSAFE -> "Blocked for your safety" to "This address was flagged as unsafe."
        err?.kind == ErrorKind.URI -> "Invalid address" to "That address isn't valid."
        err?.kind == ErrorKind.PROXY -> "Proxy problem" to "A proxy server refused the connection."
        else -> "This page couldn't be loaded" to "An unexpected problem occurred while loading it."
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        err?.url?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text(UrlTools.hostOf(it) ?: it.take(60), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = { vm.reload() }) { Text(if (tab.crashed) "Reload page" else "Try again") }
        err?.httpFallback?.let { fb ->
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.tryHttpFallback() }) { Text("Try unencrypted HTTP instead") }
            Text("Only do this for sites you trust. Traffic over HTTP can be read and modified by others.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun SuggestionList(items: List<Suggestion>, currentQuery: String, searchLabel: String, onPick: (Suggestion) -> Unit, onSearch: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (currentQuery.isNotBlank()) item {
            ListItem(headlineContent = { Text("Search for \"$currentQuery\"") }, supportingContent = { Text(searchLabel) },
                leadingContent = { Icon(Icons.Filled.Search, null) }, modifier = Modifier.clickable { onSearch() })
        }
        items(items, key = { it.kind.name + it.action + (it.tabId ?: "") }) { s ->
            ListItem(
                headlineContent = { Text(s.title, maxLines = 1) },
                supportingContent = { Text(s.subtitle, maxLines = 1) },
                leadingContent = {
                    when (s.kind) {
                        Suggestion.Kind.COMMAND -> Icon(Icons.Filled.Settings, null)
                        Suggestion.Kind.TAB -> Icon(Icons.Filled.List, null)
                        Suggestion.Kind.BOOKMARK -> Icon(Icons.Filled.Star, null)
                        Suggestion.Kind.REMOTE, Suggestion.Kind.SEARCH -> Icon(Icons.Filled.Search, null)
                        Suggestion.Kind.HISTORY -> SiteTile(UrlTools.hostOf(s.action), 28.dp)
                    }
                },
                modifier = Modifier.clickable { onPick(s) },
            )
        }
    }
}

/** Shows the oldest pending request that needs a decision. */
@Composable
fun PromptHost(vm: BrowserViewModel) {
    val requests by vm.requests.collectAsState()
    val r = requests.firstOrNull() ?: return
    when (r) {
        is PromptRequest.Alert -> AlertDialog(onDismissRequest = { vm.resolveRequest(r, true) },
            title = { Text(r.title.ifBlank { "Message from the page" }) }, text = { Text(r.message.take(2000)) },
            confirmButton = { TextButton(onClick = { vm.resolveRequest(r, true) }) { Text("OK") } })
        is PromptRequest.Confirm -> ConfirmDialog(r.title.ifBlank { "Confirm" }, r.message.take(2000), "OK", { vm.resolveRequest(r, true) }, { vm.resolveRequest(r, false) })
        is PromptRequest.Permission -> ConfirmDialog("Allow ${r.origin} to ${r.what.replaceFirstChar { it.lowercase() }}?",
            "You can change this later from the site details sheet.", "Allow", { vm.resolveRequest(r, true) }, { vm.resolveRequest(r, false) }, "Don't allow")
        is PromptRequest.External -> ConfirmDialog("Open another app?", "This page wants to open a link in another app. Only continue if you expected this.",
            "Open", { vm.resolveRequest(r, true) }, { vm.resolveRequest(r, false) })
        is PromptRequest.FilePick -> {
            val single = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> vm.resolveFile(r, uri?.let { listOf(it) }) }
            val multi = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> vm.resolveFile(r, uris.ifEmpty { null }) }
            LaunchedEffect(r) {
                val mimes = r.mimeTypes.ifEmpty { listOf("*/*") }.toTypedArray()
                if (r.multiple) multi.launch(mimes) else single.launch(mimes)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextSheet(info: ContextMenuInfo, vm: BrowserViewModel, onShare: (String) -> Unit) {
    val ctx = LocalContext.current
    ModalBottomSheet(onDismissRequest = { vm.dismissContextMenu() }) {
        Column(Modifier.padding(bottom = 24.dp)) {
            info.linkUri?.let { link ->
                Text(UrlTools.hostOf(link) ?: link, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                SheetAction("Open in new tab") { vm.openInNewTab(link, info.isPrivate); vm.dismissContextMenu() }
                if (!info.isPrivate) SheetAction("Open in private tab") { vm.openInNewTab(link, true); vm.dismissContextMenu() }
                SheetAction("Copy link") { copyText(ctx, link); vm.dismissContextMenu() }
                SheetAction("Share link") { onShare(link); vm.dismissContextMenu() }
            }
            info.imageUri?.let { img ->
                SheetAction("Open image in new tab") { vm.openInNewTab(img, info.isPrivate); vm.dismissContextMenu() }
                SheetAction("Copy image address") { copyText(ctx, img); vm.dismissContextMenu() }
            }
        }
    }
}

@Composable
fun SheetAction(label: String, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(label) }, modifier = Modifier.clickable(onClick = onClick), colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
}

fun copyText(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("WYRIX", text))
}

fun shareText(ctx: Context, text: String) {
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteInfoSheet(tab: TabState, vm: BrowserViewModel, onDismiss: () -> Unit) {
    val perms by vm.sitePerms.collectAsState()
    val settings by vm.settings.collectAsState()
    var exception by remember(tab.id, tab.url) { mutableStateOf(false) }
    LaunchedEffect(tab.id, tab.url) { exception = vm.siteException() }
    val parts = UrlTools.hostParts(tab.url)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(parts?.host ?: "Site", style = MaterialTheme.typography.titleLarge)
            parts?.unicodeHint?.let { Text("Internationalized name: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(8.dp))
            val conn = when (tab.secure) { true -> "Secure connection (HTTPS)"; false -> "Not secure. Don't enter passwords or personal data."; null -> "Connection status unknown" }
            Text(conn, color = if (tab.secure == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("Tracking protection: ${settings?.protection?.name?.lowercase()?.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.titleMedium)
            Text("${tab.blocked} trackers blocked on this page", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (settings?.protection != ProtectionLevel.OFF) {
                Row(Modifier.fillMaxWidth().clickable { vm.toggleTrackingForTab() }, verticalAlignment = Alignment.CenterVertically) {
                    Text("Turn off for this tab (until closed)", Modifier.weight(1f)); Switch(tab.trackingOff, { vm.toggleTrackingForTab() })
                }
                Row(Modifier.fillMaxWidth().clickable { exception = !exception; vm.setSiteException(exception) }, verticalAlignment = Alignment.CenterVertically) {
                    Text("Always allow trackers on this site", Modifier.weight(1f)); Switch(exception, { exception = it; vm.setSiteException(it) })
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("Permissions", style = MaterialTheme.typography.titleMedium)
            val list = perms[tab.id].orEmpty()
            if (list.isEmpty()) Text("No site permissions set.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            list.forEach { p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${p.kind}: ${p.state}", Modifier.weight(1f))
                    TextButton(onClick = { vm.resetPermission(p) }) { Text("Reset") }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Row(Modifier.fillMaxWidth().clickable { vm.toggleDesktop() }, verticalAlignment = Alignment.CenterVertically) {
                Text("Request desktop site (remembered for this site)", Modifier.weight(1f)); Switch(tab.desktopMode, { vm.toggleDesktop() })
            }
            TextButton(onClick = { vm.clearSiteData(); onDismiss() }) { Text("Clear cookies and data for this site") }
        }
    }
}
