package com.wyrix.browser

import android.content.ComponentCallbacks2
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wyrix.browser.ui.browser.BrowserScreen
import com.wyrix.browser.ui.onboarding.OnboardingScreen
import com.wyrix.browser.ui.theme.WyrixTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: BrowserViewModel by viewModels { BrowserViewModel.Factory(application as WyrixApp) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Touch the runtime early so engine start-up overlaps with first composition.
        vm.runtime
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val ready by vm.ready.collectAsStateWithLifecycle()
            val privateMode by vm.privateMode.collectAsStateWithLifecycle()
            val s = settings
            if (s != null && ready) {
                WyrixTheme(s, privateMode) {
                    if (!s.onboardingDone) OnboardingScreen(vm) else BrowserScreen(vm)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.secureWindow.collect { secure ->
                    if (secure) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } }
                launch { vm.fullscreen.collect { applyFullscreen(it) } }
            }
        }
        if (savedInstanceState == null) deliver(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deliver(intent)
    }

    private fun deliver(i: Intent?) {
        i ?: return
        vm.handleIntent(i.action, i.dataString, i.getStringExtra(Intent.EXTRA_TEXT))
    }

    private fun applyFullscreen(on: Boolean) {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        if (on) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else c.show(WindowInsetsCompat.Type.systemBars())
    }

    /** Hardware-keyboard shortcuts: Ctrl+T new tab, Ctrl+W close tab, Ctrl+R reload, Ctrl+L focus address (opens switcher-free edit via home search). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_T -> { vm.newTab(event.isShiftPressed); return true }
                KeyEvent.KEYCODE_W -> { vm.tabs.value.selectedId?.let { vm.closeTab(it) }; return true }
                KeyEvent.KEYCODE_R -> { vm.reload(); return true }
                KeyEvent.KEYCODE_N -> { vm.newTab(event.isShiftPressed); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStart() {
        super.onStart()
        vm.onForegrounded()
    }

    override fun onStop() {
        super.onStop()
        vm.onBackgrounded()
        vm.persistNow()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) vm.onTrimMemory(level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
    }
}
