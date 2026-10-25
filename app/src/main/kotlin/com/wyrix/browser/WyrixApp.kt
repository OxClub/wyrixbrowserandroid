package com.wyrix.browser

import android.app.Application
import com.wyrix.browser.data.BookmarkRepository
import com.wyrix.browser.data.DownloadCoordinator
import com.wyrix.browser.data.HistoryRepository
import com.wyrix.browser.data.OrganizationRepository
import com.wyrix.browser.data.SettingsStore
import com.wyrix.browser.data.TabRepository
import com.wyrix.browser.data.WyrixDatabase
import com.wyrix.browser.engine.EngineHolder
import com.wyrix.browser.engine.SessionManager
import com.wyrix.browser.engine.TabSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container (no DI framework: fewer moving parts, trivially inspectable). */
class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val db = WyrixDatabase.build(app)
    val settingsStore = SettingsStore(app)
    val history = HistoryRepository(db.history())
    val bookmarks = BookmarkRepository(db.bookmarks())
    val organization = OrganizationRepository(db.organization())
    val tabRepository = TabRepository(db.tabs())
    val downloads = DownloadCoordinator(app, db.downloads(), appScope)
    val downloadDao = db.downloads()
    val reader = com.wyrix.browser.data.ReaderStore(app)
    val engine = EngineHolder(app)
    val vault = com.wyrix.browser.data.VaultStore(app)
    val loginHooks = com.wyrix.browser.engine.LoginHooks()
    val sessions = SessionManager(engine)

    init {
        // Must happen before anything touches engine.runtime (it is created lazily).
        engine.loginStorage = com.wyrix.browser.engine.LoginStorageBridge(vault, loginHooks, appScope)
    }
}

class WyrixApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        TabSession.appContext = applicationContext
        container = AppContainer(this)
    }
}
