package com.wyrix.browser.engine

import android.app.Application
import com.wyrix.browser.data.ProtectionLevel
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.StorageController.ClearFlags

/**
 * Owns the single GeckoRuntime for this process (GeckoView allows exactly one). Created lazily so
 * the process can start without paying for engine start-up until a page is actually needed.
 */
class EngineHolder(private val app: Application) {
    val runtime: GeckoRuntime by lazy {
        GeckoRuntime.create(
            app,
            GeckoRuntimeSettings.Builder()
                .aboutConfigEnabled(false)
                .remoteDebuggingEnabled(false)
                .consoleOutput(false)
                .build(),
        )
    }

    fun applyProtection(level: ProtectionLevel) {
        val cb = runtime.settings.contentBlocking
        when (level) {
            ProtectionLevel.OFF -> {
                cb.setEnhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.NONE)
                cb.setAntiTracking(ContentBlocking.AntiTracking.NONE)
            }
            ProtectionLevel.BALANCED -> {
                cb.setEnhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.DEFAULT)
                cb.setAntiTracking(ContentBlocking.AntiTracking.DEFAULT)
            }
            ProtectionLevel.STRICT -> {
                cb.setEnhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT)
                cb.setAntiTracking(ContentBlocking.AntiTracking.STRICT)
            }
        }
    }

    suspend fun isSiteException(session: GeckoSession): Boolean =
        runtime.contentBlockingController.checkException(session).awaitOrNull() ?: false

    fun setSiteException(session: GeckoSession, enabled: Boolean) {
        if (enabled) runtime.contentBlockingController.addException(session)
        else runtime.contentBlockingController.removeException(session)
    }

    fun resetPermission(perm: GeckoSession.PermissionDelegate.ContentPermission) {
        runtime.storageController.setPermission(perm, GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT)
    }

    suspend fun clearSiteData(host: String) {
        runtime.storageController.clearDataFromHost(host, ClearFlags.ALL).awaitOrNull()
    }

    suspend fun clearCookiesAndSiteData() {
        runtime.storageController.clearData(
            ClearFlags.COOKIES or ClearFlags.DOM_STORAGES or ClearFlags.SITE_DATA,
        ).awaitOrNull()
    }

    suspend fun clearCache() {
        runtime.storageController.clearData(ClearFlags.NETWORK_CACHE or ClearFlags.IMAGE_CACHE).awaitOrNull()
    }
}
