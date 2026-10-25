package com.wyrix.browser.engine

import com.wyrix.browser.core.LoginItem
import com.wyrix.browser.data.Settings
import com.wyrix.browser.data.VaultStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult

/** Hooks the ViewModel fills in so the engine thread-callbacks can consult settings and request a biometric unlock. */
class LoginHooks {
    var settings: () -> Settings? = { null }
    var isUnlocked: () -> Boolean = { false }
    /** Asks the UI to authenticate; calls back with the outcome (on the main thread). */
    var requestUnlock: ((Boolean) -> Unit) -> Unit = { it(false) }
}

/**
 * Connects Gecko's login-form autofill/save machinery to the encrypted vault (Autocomplete.StorageDelegate).
 * Gecko decides when forms are login forms and when to offer filling/saving; we only supply and store entries.
 */
class LoginStorageBridge(
    private val vault: VaultStore,
    private val hooks: LoginHooks,
    private val scope: CoroutineScope,
) : Autocomplete.StorageDelegate {

    private fun LoginItem.toEntry(): Autocomplete.LoginEntry =
        Autocomplete.LoginEntry.Builder().guid(id).origin(origin).formActionOrigin(formOrigin).username(username).password(password).build()

    override fun onLoginFetch(domain: String): GeckoResult<Array<Autocomplete.LoginEntry>>? {
        val s = hooks.settings()
        if (s == null || !s.autofillLogins) return GeckoResult.fromValue(emptyArray<Autocomplete.LoginEntry>())
        val result = GeckoResult<Array<Autocomplete.LoginEntry>>()
        fun deliver() {
            scope.launch {
                val entries = vault.findForHost(domain).map { it.toEntry() }.toTypedArray()
                result.complete(entries)
            }
        }
        if (s.autofillRequiresUnlock && !hooks.isUnlocked()) {
            hooks.requestUnlock { ok -> if (ok) deliver() else result.complete(emptyArray<Autocomplete.LoginEntry>()) }
        } else deliver()
        return result
    }

    override fun onLoginSave(login: Autocomplete.LoginEntry) {
        val origin = login.origin.orEmpty()
        val password = login.password.orEmpty()
        if (origin.isEmpty() || password.isEmpty()) return
        scope.launch { vault.upsert(origin, login.formActionOrigin, login.username.orEmpty(), password, id = login.guid?.takeIf { it.isNotBlank() }) }
    }

    override fun onLoginUsed(login: Autocomplete.LoginEntry, usedFields: Int) {
        val guid = login.guid ?: return
        scope.launch { vault.touch(guid) }
    }
}
