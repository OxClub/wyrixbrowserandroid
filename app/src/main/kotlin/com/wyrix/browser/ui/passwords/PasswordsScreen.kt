package com.wyrix.browser.ui.passwords

import android.app.Activity
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wyrix.browser.BrowserViewModel
import com.wyrix.browser.core.*
import com.wyrix.browser.data.VaultState
import com.wyrix.browser.security.BiometricGate
import com.wyrix.browser.ui.common.*

/** Runs [action] after the user authenticates (valid for 2 minutes). Devices with no screen lock can't authenticate, so they pass through. */
fun requireVaultAuth(ctx: android.content.Context, vm: BrowserViewModel, action: () -> Unit) {
    if (vm.isVaultUnlocked() || BiometricGate.availability(ctx) != BiometricGate.Availability.AVAILABLE) { action(); return }
    val a = ctx as? Activity ?: run { vm.toast("Can't show the unlock prompt"); return }
    BiometricGate.authenticate(a, "Unlock passwords", "Confirm it's you") { ok -> if (ok) { vm.markVaultUnlocked(); action() } else vm.toast("Not unlocked") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordsScreen(vm: BrowserViewModel) {
    val ctx = LocalContext.current
    val state by vm.vaultState.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings
    var query by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<LoginItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<LoginItem?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var exportWarn by remember { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) vm.importPasswords(ctx, u) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { u -> if (u != null) vm.exportPasswords(ctx, u) }

    ScreenScaffold("Passwords", onBack = { vm.closeScreen() }, actions = {
        IconButton(onClick = { adding = true }, enabled = state is VaultState.Ready) { Icon(Icons.Filled.Add, "Add password") }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("Import from CSV") }, { menu = false; importer.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")) })
                DropdownMenuItem({ Text("Export to CSV…") }, { menu = false; exportWarn = true })
                DropdownMenuItem({ Text("Erase vault…", color = MaterialTheme.colorScheme.error) }, { menu = false; confirmReset = true })
            }
        }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            when (val st = state) {
                VaultState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is VaultState.Unreadable -> Column(Modifier.padding(24.dp)) {
                    Text("The vault can't be opened", style = MaterialTheme.typography.titleMedium)
                    Text("Its encryption key is missing or the file is damaged (this can happen after restoring a phone backup). Nothing has been deleted automatically. If you have no way to recover it, you can erase the vault and start again.", Modifier.padding(vertical = 8.dp))
                    Button(onClick = { confirmReset = true }) { Text("Erase vault") }
                }
                is VaultState.Ready -> {
                    val items = st.items
                    val reused = remember(items) { LoginMatching.reusedIds(items) }
                    val shown = items.filter { query.isBlank() || it.host.contains(query, true) || it.username.contains(query, true) }.sortedBy { it.host }
                    OutlinedTextField(query, { query = it.take(100) }, singleLine = true, placeholder = { Text("Search passwords") }, leadingIcon = { Icon(Icons.Filled.Search, null) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
                    if (s != null) {
                        SettingSwitch("Offer to save passwords", null, s.saveLogins) { vm.setPref(com.wyrix.browser.data.Keys.SAVE_LOGINS, it) }
                        SettingSwitch("Fill saved passwords", "Websites' login forms are filled from this vault", s.autofillLogins) { vm.setPref(com.wyrix.browser.data.Keys.AUTOFILL_LOGINS, it) }
                        SettingSwitch("Ask to unlock before filling", "Fingerprint, face or screen lock", s.autofillRequiresUnlock) { on ->
                            if (!on) vm.setPref(com.wyrix.browser.data.Keys.AUTOFILL_UNLOCK, false)
                            else if (BiometricGate.availability(ctx) == BiometricGate.Availability.AVAILABLE) vm.setPref(com.wyrix.browser.data.Keys.AUTOFILL_UNLOCK, true)
                            else vm.toast("Set up a screen lock in Android Settings first")
                        }
                    }
                    if (items.isNotEmpty()) Text("${items.size} saved · ${reused.size} reused · ${items.count { PasswordStrength.rate(it.password) == Strength.WEAK }} weak", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(shown, key = { it.id }) { it2 ->
                            val weak = PasswordStrength.rate(it2.password) == Strength.WEAK
                            ListItem(
                                headlineContent = { Text(it2.host.ifBlank { it2.origin }, maxLines = 1) },
                                supportingContent = { Text(it2.username.ifBlank { "(no username)" } + (if (it2.id in reused) " · reused" else "") + (if (weak) " · weak" else ""), maxLines = 1, color = if (weak || it2.id in reused) MaterialTheme.colorScheme.error else Color.Unspecified) },
                                leadingContent = { SiteTile(it2.host, 36.dp) },
                                modifier = Modifier.clickable { detail = it2 },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                        if (shown.isEmpty()) item { EmptyState(if (items.isEmpty()) "No saved passwords" else "No matches", "Log in to a website and choose Save, add one with +, or import a CSV file from another browser.") }
                    }
                }
            }
        }
    }

    if (adding) LoginEditor(null, vm, onDismiss = { adding = false })
    edit?.let { LoginEditor(it, vm, onDismiss = { edit = null }) }
    detail?.let { d ->
        LoginDetail(d, vm, onEdit = { edit = d; detail = null }, onDismiss = { detail = null })
    }
    if (confirmReset) ConfirmDialog("Erase the vault?", "Every saved password and the encryption key will be permanently deleted. This can't be undone.", "Erase", { vm.resetVault(); confirmReset = false }, { confirmReset = false })
    if (exportWarn) ConfirmDialog("Export passwords?", "The file will contain all your passwords in plain text, readable by anyone who gets it. Save it somewhere private and delete it afterwards.", "Export", {
        exportWarn = false; requireVaultAuth(ctx, vm) { exporter.launch("wyrix-passwords.csv") }
    }, { exportWarn = false })
}

@Composable
private fun LoginDetail(d: LoginItem, vm: BrowserViewModel, onEdit: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var reveal by remember(d.id) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(d.host.ifBlank { d.origin }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Username", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.username.ifBlank { "(none)" }, Modifier.weight(1f))
                    IconButton(onClick = { vm.copySensitive(ctx, d.username, "Username") }, enabled = d.username.isNotBlank()) { Icon(WyrixIcons.Copy, "Copy username") }
                }
                Text("Password", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (reveal) d.password else "••••••••••", Modifier.weight(1f))
                    TextButton(onClick = { if (reveal) reveal = false else requireVaultAuth(ctx, vm) { reveal = true } }) { Text(if (reveal) "Hide" else "Show") }
                    IconButton(onClick = { requireVaultAuth(ctx, vm) { vm.copySensitive(ctx, d.password, "Password") } }) { Icon(WyrixIcons.Copy, "Copy password") }
                }
                if (d.note.isNotBlank()) { Text("Note", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(d.note) }
                Row { TextButton(onClick = { vm.openInNewTab(d.origin, false); vm.closeScreen(); onDismiss() }) { Text("Open site") }; TextButton(onClick = onEdit) { Text("Edit") } }
                TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
    if (confirmDelete) ConfirmDialog("Delete this password?", "It will be removed from the vault.", "Delete", { vm.deleteLogin(d.id); confirmDelete = false; onDismiss() }, { confirmDelete = false })
}

@Composable
private fun LoginEditor(existing: LoginItem?, vm: BrowserViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var origin by remember { mutableStateOf(existing?.origin ?: "https://") }
    var user by remember { mutableStateOf(existing?.username ?: "") }
    var pass by remember { mutableStateOf(existing?.password ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var show by remember { mutableStateOf(existing == null) }
    var gen by remember { mutableStateOf(GeneratorOptions()) }
    val strength = PasswordStrength.rate(pass)
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if (existing == null) "Add password" else "Edit password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(origin, { origin = it.take(2048) }, label = { Text("Website address") }, singleLine = true, isError = !UrlTools.isWebUrl(origin))
                OutlinedTextField(user, { user = it.take(300) }, label = { Text("Username or email") }, singleLine = true)
                OutlinedTextField(pass, { pass = it.take(1000) }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { if (show) show = false else requireVaultAuth(ctx, vm) { show = true } }) { Text(if (show) "Hide" else "Show") } })
                if (pass.isNotEmpty()) Text("Strength: " + strength.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall,
                    color = when (strength) { Strength.WEAK -> MaterialTheme.colorScheme.error; Strength.FAIR -> MaterialTheme.colorScheme.onSurfaceVariant; Strength.STRONG -> MaterialTheme.colorScheme.primary })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pass = PasswordGenerator.generate(gen); show = true }) { Text("Generate") }
                    Spacer(Modifier.width(8.dp)); Text("${gen.length}", Modifier.width(24.dp))
                    Slider(gen.length.toFloat(), { gen = gen.copy(length = it.toInt()) }, valueRange = PasswordGenerator.MIN_LENGTH.toFloat()..32f, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(note, { note = it.take(1000) }, label = { Text("Note (optional)") })
            }
        },
        confirmButton = { TextButton(onClick = { vm.saveLogin(origin, user, pass, note, existing?.id); onDismiss() }, enabled = UrlTools.isWebUrl(origin) && pass.isNotEmpty()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
