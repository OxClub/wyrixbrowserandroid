package com.wyrix.browser.ui.security

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wyrix.browser.security.BiometricGate
import com.wyrix.browser.ui.common.WyrixEmblem
import com.wyrix.browser.ui.common.WyrixIcons

/** Opaque cover that blocks all interaction until the user authenticates. Content behind it is also detached by the caller. */
@Composable
fun LockOverlay(privateOnly: Boolean, onUnlocked: () -> Unit) {
    val ctx = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        val activity = ctx as? Activity ?: return@LaunchedEffect
        BiometricGate.authenticate(activity, if (privateOnly) "Unlock private tabs" else "Unlock WYRIX", "Confirm it's you") { ok ->
            if (ok) onUnlocked() else failed = true
        }
    }
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            WyrixEmblem(72.dp)
            Spacer(Modifier.height(16.dp))
            Text(if (privateOnly) "Private tabs are locked" else "WYRIX is locked", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("Use your fingerprint, face or screen lock to continue.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            Button(onClick = { failed = false; attempt++ }) {
                Icon(WyrixIcons.Fingerprint, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(if (failed) "Try again" else "Unlock")
            }
        }
    }
}
