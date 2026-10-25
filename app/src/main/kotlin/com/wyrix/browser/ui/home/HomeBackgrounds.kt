package com.wyrix.browser.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import com.wyrix.browser.data.AccentPreset
import com.wyrix.browser.ui.theme.primary
import com.wyrix.browser.ui.theme.secondary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Original generated artwork for the home screen (no stock photos, nothing downloaded; tiny and resolution-independent). */
object HomeBackgrounds {
    val all: List<Pair<String, String>> = listOf(
        "aurora" to "Aurora", "dusk" to "Dusk", "mesh" to "Mesh glow", "topo" to "Contours",
        "grid" to "Horizon", "stars" to "Starfield", "none" to "Plain",
    )
    const val CUSTOM = "custom"
    fun label(id: String) = if (id == CUSTOM) "My photo" else all.firstOrNull { it.first == id }?.second ?: "Aurora"
}

private val Ink = Color(0xFF05060A)

private fun DrawScope.glow(cx: Float, cy: Float, r: Float, c: Color, a: Float = 0.5f) {
    val center = Offset(size.width * cx, size.height * cy); val radius = r * size.maxDimension
    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), center = center, radius = radius), radius = radius, center = center)
}

private fun DrawScope.aurora(p: Color, s: Color) {
    drawRect(Brush.verticalGradient(listOf(Ink, Color(0xFF0A1226), Ink)))
    glow(0.15f, 0.12f, 0.6f, p, 0.5f); glow(0.9f, 0.28f, 0.55f, s, 0.45f); glow(0.45f, 0.5f, 0.5f, Color(0xFF22D3EE), 0.22f)
    for (k in 0 until 3) {
        val path = Path()
        val baseY = size.height * (0.16f + k * 0.07f)
        path.moveTo(-20f, baseY)
        var x = -20f
        while (x <= size.width + 20f) { path.lineTo(x, baseY + size.height * 0.05f * sin(x / size.width * 2f * PI.toFloat() * 1.4f + k)); x += 12f }
        drawPath(path, Brush.horizontalGradient(listOf(p.copy(alpha = 0f), p.copy(alpha = 0.35f), s.copy(alpha = 0.35f), s.copy(alpha = 0f))), style = Stroke(width = size.height * 0.05f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.dusk() {
    drawRect(Brush.verticalGradient(0f to Color(0xFF0B1030), 0.45f to Color(0xFF3B1E6E), 0.75f to Color(0xFFB5476B), 1f to Color(0xFFF59E5B)))
    glow(0.5f, 0.66f, 0.45f, Color(0xFFFFC38A), 0.7f)
    val ridges = listOf(0.70f to Color(0xFF2A1B4A), 0.78f to Color(0xFF1B1235), 0.88f to Color(0xFF0C0A1C))
    ridges.forEachIndexed { i, (top, color) ->
        val path = Path(); path.moveTo(0f, size.height)
        var x = 0f
        while (x <= size.width + 8f) {
            val y = size.height * top + size.height * 0.035f * sin(x / size.width * 2f * PI.toFloat() * (1.2f + i * 0.7f) + i * 1.7f) + size.height * 0.02f * sin(x / size.width * 17f + i)
            path.lineTo(x, y); x += 8f
        }
        path.lineTo(size.width, size.height); path.close(); drawPath(path, color)
    }
}

private fun DrawScope.mesh(p: Color, s: Color) {
    drawRect(Ink)
    glow(0.0f, 0.0f, 0.8f, p, 0.6f); glow(1.0f, 0.15f, 0.7f, s, 0.5f); glow(0.1f, 0.85f, 0.7f, Color(0xFF22D3EE), 0.3f); glow(0.95f, 0.9f, 0.8f, p, 0.35f)
}

private fun DrawScope.topo(p: Color) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF070A12), Ink)))
    val cx = size.width * 0.7f; val cy = size.height * 0.3f
    for (i in 1..26) {
        val path = Path(); val r = size.minDimension * 0.04f * i
        for (step in 0..120) {
            val t = step / 120f * 2f * PI.toFloat()
            val wob = 1f + 0.10f * sin(3f * t + i * 0.35f) + 0.05f * sin(5f * t - i * 0.2f)
            val x = cx + r * 1.25f * cos(t) * wob; val y = cy + r * sin(t) * wob
            if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, p.copy(alpha = (0.05f + 0.10f * (1f - i / 26f))), style = Stroke(width = 1.6f))
    }
}

private fun DrawScope.horizonGrid(p: Color, s: Color) {
    val horizon = size.height * 0.52f
    drawRect(Brush.verticalGradient(0f to Color(0xFF0A0620), 0.52f to Color(0xFF2A1250), 0.52f to Ink, 1f to Ink))
    glow(0.5f, 0.52f, 0.5f, s, 0.55f)
    drawCircle(Brush.verticalGradient(listOf(s, p), startY = horizon - size.width * 0.18f, endY = horizon), radius = size.width * 0.16f, center = Offset(size.width / 2f, horizon - size.width * 0.04f), alpha = 0.9f)
    drawRect(Ink, topLeft = Offset(0f, horizon), size = Size(size.width, size.height - horizon))
    var y = horizon; var gap = size.height * 0.008f
    while (y < size.height) { drawLine(p.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.4f); gap *= 1.28f; y += gap }
    for (k in -14..14) drawLine(p.copy(alpha = 0.28f), Offset(size.width / 2f + k * 6f, horizon), Offset(size.width / 2f + k * size.width * 0.16f, size.height), strokeWidth = 1.2f)
}

private fun DrawScope.starfield(p: Color, s: Color) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF02030A), Color(0xFF0A0F24), Ink)))
    glow(0.25f, 0.2f, 0.55f, p, 0.22f); glow(0.8f, 0.45f, 0.5f, s, 0.2f)
    val rnd = Random(7)
    repeat(170) {
        val x = rnd.nextFloat() * size.width; val y = rnd.nextFloat() * size.height
        val r = 0.5f + rnd.nextFloat() * 1.5f; val a = 0.25f + rnd.nextFloat() * 0.7f
        drawCircle(Color.White.copy(alpha = a), radius = r, center = Offset(x, y))
        if (r > 1.7f) drawCircle(Color.White.copy(alpha = 0.12f), radius = r * 4f, center = Offset(x, y))
    }
}

/** Draws the chosen backdrop plus a legibility scrim so text and tiles on top stay readable on any style. */
@Composable
fun HomeBackdrop(style: String, accent: AccentPreset, photo: ImageBitmap?, scrim: Color, modifier: Modifier = Modifier.fillMaxSize()) {
    val p = accent.primary(); val s = accent.secondary()
    Box(modifier) {
        if (style == HomeBackgrounds.CUSTOM && photo != null) {
            Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (style != "none") {
            Canvas(Modifier.fillMaxSize()) {
                when (style) {
                    "dusk" -> dusk(); "mesh" -> mesh(p, s); "topo" -> topo(p); "grid" -> horizonGrid(p, s); "stars" -> starfield(p, s)
                    else -> aurora(p, s)
                }
            }
        }
        if (style != "none") Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(0f to scrim.copy(alpha = 0.15f), 0.4f to scrim.copy(alpha = 0.45f), 1f to scrim.copy(alpha = 0.78f)))
        }
    }
}
