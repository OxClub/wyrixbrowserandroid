package com.wyrix.browser.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.wyrix.browser.core.LoginCsv
import com.wyrix.browser.core.CsvLogin
import com.wyrix.browser.core.LoginItem
import com.wyrix.browser.core.LoginMatching
import com.wyrix.browser.core.UrlTools
import com.wyrix.browser.core.VaultCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

sealed interface VaultState {
    data object Loading : VaultState
    data class Ready(val items: List<LoginItem>) : VaultState
    /** The file exists but can't be decrypted/parsed (e.g. keystore key lost). It is never overwritten automatically. */
    data class Unreadable(val reason: String) : VaultState
}

/** AES-256-GCM with a non-exportable Android Keystore key. File layout: "WVLT1" | iv(12) | ciphertext+tag. */
internal object VaultCrypto {
    private const val ALIAS = "wyrix_vault_key_v1"
    private val MAGIC = "WVLT1".toByteArray()
    private val AAD = "wyrix-vault-v1".toByteArray()

    private fun key(create: Boolean): SecretKey? {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        if (!create) return null
        fun spec(strongBox: Boolean) = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256)
            .setRandomizedEncryptionRequired(true).apply { if (strongBox && android.os.Build.VERSION.SDK_INT >= 28) setIsStrongBoxBacked(true) }.build()
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        return try { gen.init(spec(true)); gen.generateKey() } catch (e: Exception) { gen.init(spec(false)); gen.generateKey() }
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(true)!!)
        c.updateAAD(AAD)
        val ct = c.doFinal(plain)
        return MAGIC + c.iv + ct
    }

    fun decrypt(file: ByteArray): ByteArray {
        if (file.size < MAGIC.size + 12 + 16 || !file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw IOException("not a vault file")
        val k = key(false) ?: throw IOException("vault key is missing")
        val iv = file.copyOfRange(MAGIC.size, MAGIC.size + 12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, iv))
        c.updateAAD(AAD)
        return try { c.doFinal(file, MAGIC.size + 12, file.size - MAGIC.size - 12) } catch (e: Exception) { throw IOException("vault could not be decrypted") }
    }
}

/**
 * Encrypted password vault in app-private storage. All access is serialised by a mutex; writes are atomic
 * (temp file + rename). A vault that fails to decrypt is reported as Unreadable and left untouched on disk.
 */
class VaultStore(context: Context) {
    private val file = File(context.filesDir, "vault.bin")
    private val mutex = Mutex()
    private var cache: List<LoginItem>? = null
    private val _state = MutableStateFlow<VaultState>(VaultState.Loading)
    val state: StateFlow<VaultState> = _state.asStateFlow()

    private fun now() = System.currentTimeMillis()

    private fun loadLocked(): List<LoginItem> {
        cache?.let { return it }
        if (!file.exists()) { cache = emptyList(); _state.value = VaultState.Ready(emptyList()); return emptyList() }
        return try {
            val items = VaultCodec.decode(VaultCrypto.decrypt(file.readBytes()))
            cache = items; _state.value = VaultState.Ready(items); items
        } catch (e: Exception) {
            _state.value = VaultState.Unreadable(e.message ?: "unreadable")
            throw IOException("vault unreadable")
        }
    }

    private fun saveLocked(items: List<LoginItem>) {
        val tmp = File(file.parentFile, "vault.tmp")
        tmp.writeBytes(VaultCrypto.encrypt(VaultCodec.encode(items)))
        if (!tmp.renameTo(file)) { file.delete(); if (!tmp.renameTo(file)) throw IOException("could not write vault") }
        cache = items; _state.value = VaultState.Ready(items)
    }

    private suspend fun <T> locked(block: () -> T): T? = withContext(Dispatchers.IO) { mutex.withLock { try { block() } catch (e: Exception) { null } } }

    suspend fun refresh() { locked { loadLocked() } }
    suspend fun all(): List<LoginItem> = locked { loadLocked() } ?: emptyList()
    suspend fun findForHost(domain: String): List<LoginItem> = all().filter { LoginMatching.matches(it, domain) }.sortedByDescending { it.lastUsed }

    private fun clean(s: String, max: Int) = s.filter { it != '\u0000' }.take(max)

    /** Adds or updates (same site + username). Returns false if the vault is unreadable or full. */
    suspend fun upsert(origin: String, formOrigin: String?, username: String, password: String, note: String? = null, id: String? = null): Boolean =
        locked {
            val items = loadLocked()
            if (!UrlTools.isWebUrl(origin) || password.isEmpty() || password.length > 1000) return@locked false
            val u = clean(username, 300); val t = now()
            val existing = items.firstOrNull { it.id == id } ?: items.firstOrNull { LoginMatching.sameAccount(it, origin, u) }
            val next = if (existing != null) items.map {
                if (it.id == existing.id) it.copy(origin = clean(origin, 2048), username = u, password = password, note = note?.let { n -> clean(n, 1000) } ?: it.note, updatedAt = t) else it
            } else {
                if (items.size >= VaultCodec.MAX_ITEMS) return@locked false
                items + LoginItem(UUID.randomUUID().toString(), clean(origin, 2048), clean(formOrigin ?: origin, 2048), u, password, clean(note.orEmpty(), 1000), t, t, t)
            }
            saveLocked(next); true
        } ?: false

    suspend fun delete(id: String) { locked { saveLocked(loadLocked().filterNot { it.id == id }) } }
    suspend fun touch(id: String) { locked { val items = loadLocked(); if (items.any { it.id == id }) saveLocked(items.map { if (it.id == id) it.copy(lastUsed = now()) else it }) } }

    /** Imports parsed CSV rows; duplicates (same site + username) are updated rather than repeated. Returns (added, updated). */
    suspend fun importCsv(rows: List<CsvLogin>): Pair<Int, Int> = locked {
        var items = loadLocked(); var added = 0; var updated = 0; val t = now()
        for (r in rows) {
            val origin = originOf(r.url)
            val u = clean(r.username, 300)
            val ex = items.firstOrNull { LoginMatching.sameAccount(it, origin, u) }
            if (ex != null) { items = items.map { if (it.id == ex.id) it.copy(password = r.password, note = clean(r.note, 1000).ifEmpty { it.note }, updatedAt = t) else it }; updated++ }
            else if (items.size < VaultCodec.MAX_ITEMS) { items = items + LoginItem(UUID.randomUUID().toString(), origin, origin, u, r.password, clean(r.note, 1000), t, t, t); added++ }
        }
        saveLocked(items); added to updated
    } ?: (0 to 0)

    suspend fun exportCsv(): String = LoginCsv.write(all().map { CsvLogin(it.origin, it.username, it.password, it.note) })

    /** Deletes the vault file and its key. Only for an unreadable vault or an explicit user reset. */
    suspend fun resetEverything() = withContext(Dispatchers.IO) {
        mutex.withLock {
            file.delete(); File(file.parentFile, "vault.tmp").delete()
            runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("wyrix_vault_key_v1") }
            cache = emptyList(); _state.value = VaultState.Ready(emptyList())
        }
    }

    private fun originOf(url: String): String {
        val m = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*)://([^/?#]*)").find(url) ?: return url
        return m.groupValues[1].lowercase() + "://" + m.groupValues[2].lowercase()
    }
}
