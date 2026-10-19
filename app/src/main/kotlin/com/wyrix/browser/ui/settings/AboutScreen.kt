package com.wyrix.browser.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wyrix.browser.BrowserViewModel
import com.wyrix.browser.BuildConfig
import com.wyrix.browser.ui.common.ScreenScaffold
import com.wyrix.browser.ui.common.SectionHeader
import com.wyrix.browser.ui.common.WyrixEmblem

private val LICENSES = listOf(
    "GeckoView / Gecko (Mozilla)" to "Mozilla Public License 2.0",
    "AndroidX, Jetpack Compose, Room, DataStore (Google)" to "Apache License 2.0",
    "Kotlin and kotlinx.coroutines (JetBrains)" to "Apache License 2.0",
)

private val HELP = listOf(
    "Search or open a site" to "Tap the address bar. Text that looks like a web address opens it over HTTPS; anything else is searched.",
    "Private tabs" to "Open one from the menu or tab switcher. Private tabs never save history, cookies or site data, and are cleared when closed.",
    "Spaces and groups" to "Spaces (Personal, Work, Study) keep tabs apart. Group related tabs inside a space from a tab's menu.",
    "Tracking protection" to "Tap the lock in the address bar to see what's blocked and to relax protection for a site that breaks.",
    "Move bookmarks in" to "Export bookmarks from your old browser as an HTML file, then use Settings → Import bookmarks.",
)

@Composable
fun AboutScreen(vm: BrowserViewModel) {
    ScreenScaffold("About", onBack = { vm.closeScreen() }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    WyrixEmblem(72.dp)
                    Text("WYRIX Browser", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                    Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium)
                    Text("GeckoView ${BuildConfig.GECKOVIEW_VERSION}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { SectionHeader("Privacy") }
            item {
                Text("WYRIX has no accounts, analytics, advertising or crash reporting. Browsing data stays on this device and is excluded from Android backups. " +
                    "The only network requests the app makes itself are the optional search suggestions you can turn on in Settings; everything else is page loading by the engine.",
                    Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
            }
            item { SectionHeader("Help") }
            HELP.forEach { (t, b) -> item { ListItem(headlineContent = { Text(t) }, supportingContent = { Text(b) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent)) } }
            item { SectionHeader("Open-source licenses") }
            LICENSES.forEach { (n, l) -> item { ListItem(headlineContent = { Text(n) }, supportingContent = { Text(l) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent)) } }
            item { Text("Full notices ship in THIRD_PARTY_NOTICES.md in the source tree.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
