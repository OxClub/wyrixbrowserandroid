package com.wyrix.browser

import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real-engine smoke test: loads a data: page and observes title + page-stop callbacks. Needs a device/emulator. */
@RunWith(AndroidJUnit4::class)
class GeckoSessionTest {
    @Test fun loadsLocalPageAndReportsTitle() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WyrixApp
        val stopped = CountDownLatch(1)
        var title: String? = null
        var ok = false
        var session: GeckoSession? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val s = GeckoSession()
            s.progressDelegate = object : GeckoSession.ProgressDelegate {
                override fun onPageStop(session: GeckoSession, success: Boolean) { ok = success; stopped.countDown() }
            }
            s.contentDelegate = object : GeckoSession.ContentDelegate {
                override fun onTitleChange(session: GeckoSession, t: String?) { title = t }
            }
            s.open(app.container.engine.runtime)
            s.loadUri("data:text/html;charset=utf-8,%3Ctitle%3EWYRIX%20smoke%3C%2Ftitle%3E%3Cp%3Ehello")
            session = s
        }
        assertTrue("page did not finish loading", stopped.await(90, TimeUnit.SECONDS))
        assertTrue(ok)
        // Title callbacks can land just after page stop; give the main looper a moment.
        val settled = CountDownLatch(1); Handler(Looper.getMainLooper()).postDelayed({ settled.countDown() }, 1000); settled.await(5, TimeUnit.SECONDS)
        assertEquals("WYRIX smoke", title)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { session?.close() }
    }
}
