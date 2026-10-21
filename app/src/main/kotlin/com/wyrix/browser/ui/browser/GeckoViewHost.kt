package com.wyrix.browser.ui.browser

import android.graphics.Color as AColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/** Single GeckoView whose attached session is swapped when the selected tab changes. */
@Composable
fun GeckoViewHost(session: GeckoSession?, runtime: GeckoRuntime, modifier: Modifier = Modifier, onView: (GeckoView) -> Unit = {}) {
    AndroidView(
        modifier = modifier,
        factory = { ctx -> GeckoView(ctx).also(onView) },
        update = { view ->
            if (view.session !== session) {
                if (view.session != null) view.releaseSession()
                if (session != null) {
                    if (!session.isOpen) session.open(runtime)
                    view.coverUntilFirstPaint(AColor.BLACK)
                    view.setSession(session)
                }
            }
        },
        onRelease = { view -> view.releaseSession() },
    )
}
