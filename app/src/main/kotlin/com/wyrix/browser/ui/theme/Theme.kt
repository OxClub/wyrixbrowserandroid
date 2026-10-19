package com.wyrix.browser.ui.theme

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.wyrix.browser.data.AccentPreset
import com.wyrix.browser.data.Settings
import com.wyrix.browser.data.ThemeMode
import android.app.Activity

/** Accent presets (original palette). */
fun AccentPreset.primary(): Color = when (this) {
    AccentPreset.ELECTRIC -> Color(0xFF5B8CFF); AccentPreset.ION -> Color(0xFF22D3EE)
    AccentPreset.AURORA -> Color(0xFF34D399); AccentPreset.EMBER -> Color(0xFFFB923C)
}
fun AccentPreset.secondary(): Color = when (this) {
    AccentPreset.ELECTRIC -> Color(0xFF9B6BFF); AccentPreset.ION -> Color(0xFF6366F1)
    AccentPreset.AURORA -> Color(0xFF38BDF8); AccentPreset.EMBER -> Color(0xFFF472B6)
}

data class Motion(val reduced: Boolean) { fun ms(base: Int): Int = if (reduced) 0 else base }
val LocalMotion = compositionLocalOf { Motion(false) }

object Dimens { val Chrome = 56.dp; val Pill = 24.dp; val Pad = 16.dp; val Tile = 64.dp }

private val PrivateViolet = Color(0xFFB388FF)

fun wyrixColors(dark: Boolean, trueBlack: Boolean, accent: AccentPreset, highContrast: Boolean, private: Boolean): ColorScheme {
    val primary = if (private) PrivateViolet else accent.primary()
    val secondary = if (private) Color(0xFF7C4DFF) else accent.secondary()
    return if (dark) {
        val bg = when { private -> Color(0xFF0B0614); trueBlack -> Color.Black; else -> Color(0xFF05060A) }
        val surface = when { private -> Color(0xFF150B24); trueBlack -> Color(0xFF07080C); else -> Color(0xFF0B0D14) }
        darkColorScheme(
            primary = primary, onPrimary = Color(0xFF05060A), secondary = secondary, onSecondary = Color.White,
            background = bg, onBackground = if (highContrast) Color.White else Color(0xFFE8ECF8),
            surface = surface, onSurface = if (highContrast) Color.White else Color(0xFFE8ECF8),
            surfaceVariant = if (private) Color(0xFF1F1233) else Color(0xFF141824),
            onSurfaceVariant = if (highContrast) Color(0xFFF2F4FA) else Color(0xFFA7AEC4),
            outline = if (highContrast) Color(0xFFD0D6EA) else Color(0xFF2A3042),
            error = Color(0xFFFF6B6B),
        )
    } else {
        lightColorScheme(
            primary = primary, onPrimary = Color.White, secondary = secondary, onSecondary = Color.White,
            background = Color(0xFFF6F7FB), onBackground = if (highContrast) Color.Black else Color(0xFF10131C),
            surface = Color.White, onSurface = if (highContrast) Color.Black else Color(0xFF10131C),
            surfaceVariant = Color(0xFFE9ECF5), onSurfaceVariant = if (highContrast) Color(0xFF1A1D28) else Color(0xFF50566B),
            outline = if (highContrast) Color(0xFF20242F) else Color(0xFFC3C8D8), error = Color(0xFFB3261E),
        )
    }
}

private val WyrixTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)
private val WyrixShapes = Shapes(
    small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp),
)

/** Motion is reduced if the user setting is on OR Android battery saver is active. */
@Composable
fun rememberReducedMotion(userSetting: Boolean): Boolean {
    val ctx = LocalContext.current
    var saver by remember { mutableStateOf((ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode) }
    DisposableEffect(ctx) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { saver = (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode }
        }
        ctx.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        onDispose { ctx.unregisterReceiver(receiver) }
    }
    return userSetting || saver
}

@Composable
fun WyrixTheme(settings: Settings, privateMode: Boolean, content: @Composable () -> Unit) {
    val dark = privateMode || when (settings.themeMode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    val scheme = wyrixColors(dark, settings.trueBlack, settings.accent, settings.highContrast, privateMode)
    val reduced = rememberReducedMotion(settings.reducedMotion)
    val view = LocalView.current
    SideEffect {
        val w = (view.context as? Activity)?.window ?: return@SideEffect
        val c = WindowCompat.getInsetsController(w, view)
        c.isAppearanceLightStatusBars = !dark
        c.isAppearanceLightNavigationBars = !dark
    }
    CompositionLocalProvider(LocalMotion provides Motion(reduced)) {
        MaterialTheme(colorScheme = scheme, typography = WyrixTypography, shapes = WyrixShapes, content = content)
    }
}
