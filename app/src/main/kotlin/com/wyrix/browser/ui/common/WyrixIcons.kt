package com.wyrix.browser.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Original stroke icons on a 24x24 grid (simple geometry drawn for WYRIX; tinted by the Icon composable). */
object WyrixIcons {
    private fun stroke(name: String, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        paths.forEach { d ->
            b.addPath(
                pathData = addPathNodes(d), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    val Share by lazy { stroke("share", "M12,3v12", "M8,7l4,-4l4,4", "M5,12v6a2,2 0 0 0 2,2h10a2,2 0 0 0 2,-2v-6") }
    val Copy by lazy { stroke("copy", "M9,9h9a2,2 0 0 1 2,2v8a2,2 0 0 1 -2,2H9a2,2 0 0 1 -2,-2v-8a2,2 0 0 1 2,-2z", "M5,15V6a2,2 0 0 1 2,-2h9") }
    val Download by lazy { stroke("download", "M12,3v12", "M8,11l4,4l4,-4", "M5,20h14") }
    val Reader by lazy { stroke("reader", "M5,4h14a1,1 0 0 1 1,1v14a1,1 0 0 1 -1,1H5a1,1 0 0 1 -1,-1V5a1,1 0 0 1 1,-1z", "M8,9h8", "M8,13h8", "M8,17h5") }
    val Translate by lazy { stroke("translate", "M4,6h9", "M8.5,4v2", "M6,6c0,3 2,6 6,8", "M12,6c0,3 -2,6 -6,8", "M13,20l4,-9l4,9", "M14.5,17h5") }
    val Shield by lazy { stroke("shield", "M12,3l7,3v5c0,5 -3,8 -7,10c-4,-2 -7,-5 -7,-10V6z", "M9,12l2,2l4,-4") }
    val Lock by lazy { stroke("lock", "M6,11h12a1,1 0 0 1 1,1v7a1,1 0 0 1 -1,1H6a1,1 0 0 1 -1,-1v-7a1,1 0 0 1 1,-1z", "M8,11V8a4,4 0 0 1 8,0v3") }
    val Fingerprint by lazy { stroke("fingerprint", "M7,10a5,5 0 0 1 10,0v2", "M5,13v-3a7,7 0 0 1 14,0", "M12,10v6a3,3 0 0 1 -3,3", "M9,12v3", "M15,12v2a6,6 0 0 1 -2,4.5") }
    val Bookmark by lazy { stroke("bookmark", "M7,4h10a1,1 0 0 1 1,1v15l-6,-4l-6,4V5a1,1 0 0 1 1,-1z") }
    val Plus by lazy { stroke("plus", "M12,5v14", "M5,12h14") }
    val PrivateTab by lazy { stroke("private", "M4,12c2,-4 5,-6 8,-6s6,2 8,6c-2,4 -5,6 -8,6s-6,-2 -8,-6z", "M12,9.5a2.5,2.5 0 1 0 0.01,0") }
    val Reopen by lazy { stroke("reopen", "M4,12a8,8 0 1 0 3,-6", "M4,4v4h4", "M12,8v4l3,2") }
    val Folder by lazy { stroke("folder", "M4,7a1,1 0 0 1 1,-1h4l2,2h8a1,1 0 0 1 1,1v9a1,1 0 0 1 -1,1H5a1,1 0 0 1 -1,-1z") }
    val Check by lazy { stroke("check", "M5,12l5,5l9,-10") }
}
