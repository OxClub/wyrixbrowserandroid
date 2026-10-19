package com.wyrix.browser.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wyrix.browser.BrowserViewModel
import com.wyrix.browser.core.SearchEngines
import com.wyrix.browser.data.*
import com.wyrix.browser.ui.common.WyrixEmblem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(vm: BrowserViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val st = s ?: return
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        WyrixEmblem(96.dp, st.accent)
        Text("Welcome to WYRIX", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
        Text("A fast, private browser built on Mozilla's Gecko engine. No accounts, no tracking, no ads.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))

        Group("Search engine") {
            FlowChips(SearchEngines.all.map { it.id to it.name }, st.searchEngineId) { vm.setPref(Keys.SEARCH_ENGINE, it) }
        }
        Group("Theme") {
            FlowChips(ThemeMode.entries.map { it.name to it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }, st.themeMode.name) { vm.setPref(Keys.THEME, it) }
        }
        Group("Toolbar position") {
            FlowChips(ToolbarPosition.entries.map { it.name to it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }, st.toolbarPosition.name) { vm.setPref(Keys.TOOLBAR, it) }
        }
        Group("Tracking protection") {
            FlowChips(listOf(ProtectionLevel.BALANCED.name to "Balanced", ProtectionLevel.STRICT.name to "Strict"), st.protection.name) { vm.setPref(Keys.PROTECTION, it) }
        }
        Text("You can change all of this later in Settings. To make WYRIX your default browser or import bookmarks, see Settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(16.dp))
        Button(onClick = { vm.finishOnboarding() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Start browsing") }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp)); content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlowChips(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    // Column of rows of up to 3 chips: avoids the experimental FlowRow API.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(3).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { (id, label) -> FilterChip(selected = id == selected, onClick = { onSelect(id) }, label = { Text(label) }) }
            }
        }
    }
}
