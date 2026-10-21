package com.wyrix.browser.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wyrix.browser.BrowserViewModel
import com.wyrix.browser.ReaderState
import com.wyrix.browser.core.ReaderBlock
import com.wyrix.browser.data.Keys
import com.wyrix.browser.ui.common.*

@Composable
fun ReaderScreen(vm: BrowserViewModel) {
    val state by vm.reader.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val ready = state as? ReaderState.Ready
    ScreenScaffold("Reader", onBack = { vm.closeScreen() }, actions = {
        TextButton(onClick = { vm.setPref(Keys.READER_SCALE, (s.readerScale - 10).coerceAtLeast(70)) }, enabled = s.readerScale > 70) { Text("A−") }
        TextButton(onClick = { vm.setPref(Keys.READER_SCALE, (s.readerScale + 10).coerceAtMost(220)) }, enabled = s.readerScale < 220) { Text("A+") }
        TextButton(onClick = { vm.setPref(Keys.READER_SERIF, !s.readerSerif) }) { Text(if (s.readerSerif) "Sans" else "Serif") }
        if (ready != null) TextButton(onClick = { vm.toggleSaveArticle() }) { Text(if (ready.saved) "Unsave" else "Save") }
    }) { pad ->
        when (val st = state) {
            ReaderState.Idle, ReaderState.Loading -> Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text("Preparing article…") }
            }
            is ReaderState.Failed -> Column(Modifier.padding(pad).fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text("Reader view isn't available here", style = MaterialTheme.typography.titleMedium)
                Text(st.message, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                Button(onClick = { vm.closeScreen() }, modifier = Modifier.padding(top = 16.dp)) { Text("Back to page") }
            }
            is ReaderState.Ready -> {
                val a = st.article
                val base = 17f * s.readerScale / 100f
                val family = if (s.readerSerif) FontFamily.Serif else FontFamily.SansSerif
                LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        Text(a.title, fontSize = (base * 1.6f).sp, lineHeight = (base * 2f).sp, fontWeight = FontWeight.Bold, fontFamily = family)
                        Text("${a.siteName} · ${a.minutes} min read" + if (st.saved) " · saved offline" else "", fontSize = (base * 0.8f).sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                        TextButton(onClick = { vm.openOriginalFromReader() }) { Text("Open original page") }
                    }
                    items(a.blocks.size) { i ->
                        when (val b = a.blocks[i]) {
                            is ReaderBlock.Heading -> Text(b.text, fontSize = (base * (if (b.level <= 2) 1.3f else 1.1f)).sp, fontWeight = FontWeight.SemiBold, fontFamily = family, modifier = Modifier.padding(top = 8.dp))
                            is ReaderBlock.Paragraph -> Text(b.text, fontSize = base.sp, lineHeight = (base * 1.6f).sp, fontFamily = family)
                            is ReaderBlock.Quote -> Text(b.text, fontSize = base.sp, lineHeight = (base * 1.5f).sp, fontStyle = FontStyle.Italic, fontFamily = family, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
                            is ReaderBlock.Item -> Text("•  " + b.text, fontSize = base.sp, lineHeight = (base * 1.5f).sp, fontFamily = family)
                            is ReaderBlock.Code -> Text(b.text, fontSize = (base * 0.85f).sp, fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxWidth().padding(8.dp))
                        }
                    }
                    item { Text("Text only. Images and interactive content are left out.", fontSize = (base * 0.75f).sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
fun SavedArticlesScreen(vm: BrowserViewModel) {
    val items by vm.savedArticles.collectAsStateWithLifecycle()
    ScreenScaffold("Saved articles", onBack = { vm.closeScreen() }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            items(items, key = { it.id }) { m ->
                ListItem(
                    headlineContent = { Text(m.title.ifBlank { m.url }, maxLines = 2) }, supportingContent = { Text(m.siteName, maxLines = 1) },
                    trailingContent = { IconButton(onClick = { vm.deleteSaved(m.id) }) { Icon(Icons.Filled.Delete, "Delete ${m.title}") } },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { vm.openSavedArticle(m.id) },
                )
            }
            if (items.isEmpty()) item { EmptyState("No saved articles", "Open a page, choose Reader mode from the menu, then tap Save to read it offline.") }
        }
    }
}
