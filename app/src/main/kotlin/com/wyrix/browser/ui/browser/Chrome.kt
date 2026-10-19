package com.wyrix.browser.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.wyrix.browser.core.UrlTools
import com.wyrix.browser.tabs.TabState

@Composable
fun AddressRow(
    tab: TabState?, editing: Boolean, field: TextFieldValue, onField: (TextFieldValue) -> Unit,
    onStartEdit: () -> Unit, onSubmit: () -> Unit, onSiteInfo: () -> Unit, onReloadOrStop: () -> Unit, bookmarked: Boolean,
    onToggleBookmark: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(editing) { if (editing) focus.requestFocus() }
    val loading = tab?.loading == true
    Surface(
        shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).heightIn(min = 48.dp),
    ) {
        Box {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val secure = tab?.secure
                val hasPage = tab != null && tab.hasContent && !tab.showHome
                IconButton(onClick = onSiteInfo, enabled = hasPage) {
                    when {
                        !hasPage -> Icon(Icons.Filled.Search, "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        secure == true -> Icon(Icons.Filled.Lock, "Connection secure. Open site details", tint = MaterialTheme.colorScheme.primary)
                        secure == false -> Icon(Icons.Filled.Warning, "Connection not secure. Open site details", tint = MaterialTheme.colorScheme.error)
                        else -> Icon(Icons.Filled.Info, "Open site details")
                    }
                }
                if (editing) {
                    BasicTextField(
                        value = field, onValueChange = onField, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                        modifier = Modifier.weight(1f).focusRequester(focus).semantics { contentDescription = "Address or search" },
                        decorationBox = { inner ->
                            if (field.text.isEmpty()) Text("Search or enter address", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            inner()
                        },
                    )
                    if (field.text.isNotEmpty()) IconButton(onClick = { onField(TextFieldValue("")) }) { Icon(Icons.Filled.Clear, "Clear text") }
                } else {
                    Column(Modifier.weight(1f).clickable(onClickLabel = "Edit address") { onStartEdit() }.padding(vertical = 6.dp)) {
                        Text(displayUrl(tab), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        val sub = tab?.title.orEmpty()
                        if (sub.isNotBlank() && tab?.showHome == false) Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (tab != null && tab.hasContent && !tab.showHome) {
                        IconButton(onClick = onToggleBookmark) {
                            Icon(Icons.Filled.Star, if (bookmarked) "Remove bookmark" else "Add bookmark",
                                tint = if (bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onReloadOrStop) { Icon(if (loading) Icons.Filled.Close else Icons.Filled.Refresh, if (loading) "Stop loading" else "Reload") }
                    }
                }
            }
            if (loading && !editing) {
                val p = ((tab?.progress ?: 0).coerceIn(0, 100)) / 100f
                LinearProgressIndicator(progress = { p }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp))
            }
        }
    }
}

/** Registrable domain is emphasised; subdomains dimmed. Punycode hosts are shown as-is (anti-spoofing). */
@Composable
private fun displayUrl(tab: TabState?): AnnotatedString {
    if (tab == null || !tab.hasContent || tab.showHome) return AnnotatedString("Search or enter address")
    val parts = UrlTools.hostParts(tab.url) ?: return AnnotatedString(tab.url.take(80))
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    return buildAnnotatedString {
        if (parts.subdomain.isNotEmpty()) withStyle(SpanStyle(color = dim)) { append(parts.subdomain + ".") }
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(parts.domain) }
        if (parts.isPunycode) withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) { append("  (internationalized)") }
    }
}

@Composable
fun ActionRow(
    tab: TabState?, tabCount: Int, privateMode: Boolean, onBack: () -> Unit, onForward: () -> Unit, onHome: () -> Unit,
    onTabs: () -> Unit, onMenu: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        val canBack = tab != null && ((tab.canGoBack && !tab.showHome) || (tab.showHome && tab.hasContent))
        IconButton(onClick = onBack, enabled = canBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        IconButton(onClick = onForward, enabled = tab?.canGoForward == true) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward") }
        IconButton(onClick = onHome) { Icon(Icons.Filled.Home, "Home") }
        IconButton(onClick = onTabs) {
            Box(
                Modifier.size(26.dp).border(2.dp, if (privateMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, RoundedCornerShape(7.dp))
                    .semantics { contentDescription = "Open tab switcher, $tabCount tabs" },
                contentAlignment = Alignment.Center,
            ) { Text(if (tabCount > 99) ":)" else tabCount.toString(), style = MaterialTheme.typography.labelSmall) }
        }
        Box { onMenu() }
    }
}
