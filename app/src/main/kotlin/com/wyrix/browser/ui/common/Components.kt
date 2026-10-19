package com.wyrix.browser.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wyrix.browser.core.UrlTools
import com.wyrix.browser.data.AccentPreset
import com.wyrix.browser.ui.theme.primary
import com.wyrix.browser.ui.theme.secondary

/** Original WYRIX emblem: ring + one-stroke "W". */
@Composable
fun WyrixEmblem(size: Dp, accent: AccentPreset = AccentPreset.ELECTRIC, modifier: Modifier = Modifier) {
    val brush = Brush.linearGradient(listOf(accent.primary(), accent.secondary()))
    Canvas(modifier.size(size).semantics { contentDescription = "WYRIX logo" }) {
        val s = this.size.minDimension
        drawCircle(brush, radius = s * 0.46f, style = Stroke(width = s * 0.03f))
        val p = Path().apply {
            moveTo(s * 0.26f, s * 0.34f); lineTo(s * 0.38f, s * 0.70f); lineTo(s * 0.50f, s * 0.46f)
            lineTo(s * 0.62f, s * 0.70f); lineTo(s * 0.74f, s * 0.34f)
        }
        drawPath(p, brush, style = Stroke(width = s * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Letter tile used in place of favicons (GeckoView exposes no favicon event; see docs/FEATURE_MATRIX.md). */
@Composable
fun SiteTile(host: String?, size: Dp = 40.dp, color: Color? = null, modifier: Modifier = Modifier) {
    val seed = (host ?: "").hashCode()
    val bg = color ?: Color.hsv(((seed % 360) + 360) % 360f, 0.45f, 0.75f)
    Box(modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(UrlTools.initialFor(host), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}, snackbar: SnackbarHostState? = null, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) },
        content = content,
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp).semantics { })
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ConfirmDialog(title: String, body: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit, dismissLabel: String = "Cancel") {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
    )
}

@Composable
fun TextInputDialog(title: String, initial: String = "", label: String, confirmLabel: String = "Save", onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(text, { text = it.take(200) }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

val PaletteColors = listOf(0xFF5B8CFF, 0xFF9B6BFF, 0xFF34D399, 0xFFFB923C, 0xFFF472B6, 0xFF22D3EE, 0xFFFACC15, 0xFFEF4444).map { it.toInt() }

@Composable
fun ColorRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PaletteColors.forEach { c ->
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                    .then(if (c == selected) Modifier.background(Color.Transparent).padding(3.dp).background(Color.White.copy(alpha = 0.9f), CircleShape).padding(3.dp).background(Color(c), CircleShape) else Modifier)
                    .clickable { onSelect(c) }
                    .semantics { contentDescription = if (c == selected) "Color, selected" else "Color" },
            )
        }
    }
}

@Composable
fun SettingSwitch(title: String, subtitle: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked, onChange, enabled = enabled) },
        modifier = Modifier.clickable(enabled = enabled) { onChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
fun <T> ChoiceDialog(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column {
                options.forEach { (v, label) ->
                    Row(Modifier.fillMaxWidth().clickable { onSelect(v); onDismiss() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = v == selected, onClick = { onSelect(v); onDismiss() })
                        Spacer(Modifier.width(8.dp)); Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

fun formatBytes(b: Long): String = when {
    b < 0 -> "Unknown size"
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${b / 1024} KB"
    b < 1024L * 1024 * 1024 -> String.format("%.1f MB", b / 1048576.0)
    else -> String.format("%.2f GB", b / 1073741824.0)
}
