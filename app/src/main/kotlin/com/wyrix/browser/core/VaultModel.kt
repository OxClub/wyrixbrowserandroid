package com.wyrix.browser.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

data class LoginItem(
    val id: String,
    /** Page origin the login applies to, e.g. https://example.com */
    val origin: String,
    /** Form-action origin reported by the engine (falls back to [origin]). */
    val formOrigin: String,
    val username: String,
    val password: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsed: Long,
) {
    val host: String get() = UrlTools.hostOf(origin).orEmpty()
}

/** Compact binary container for the vault contents (encrypted separately). Pure JVM so it's unit-tested. */
object VaultCodec {
    private const val VERSION = 1
    const val MAX_ITEMS = 5000

    fun encode(items: List<LoginItem>): ByteArray {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use { o ->
            o.writeByte(VERSION); o.writeInt(items.size)
            for (i in items) {
                o.writeUTF(i.id); o.writeUTF(i.origin); o.writeUTF(i.formOrigin); o.writeUTF(i.username); o.writeUTF(i.password); o.writeUTF(i.note)
                o.writeLong(i.createdAt); o.writeLong(i.updatedAt); o.writeLong(i.lastUsed)
            }
        }
        return bos.toByteArray()
    }

    /** Throws IOException on any structural problem; callers must treat that as "vault unreadable", never as "empty". */
    fun decode(bytes: ByteArray): List<LoginItem> {
        DataInputStream(ByteArrayInputStream(bytes)).use { i ->
            if (i.readUnsignedByte() != VERSION) throw IOException("unsupported vault version")
            val n = i.readInt()
            if (n < 0 || n > MAX_ITEMS) throw IOException("bad item count")
            return List(n) {
                LoginItem(i.readUTF(), i.readUTF(), i.readUTF(), i.readUTF(), i.readUTF(), i.readUTF(), i.readLong(), i.readLong(), i.readLong())
            }
        }
    }
}

object LoginMatching {
    private fun norm(host: String) = host.lowercase().removePrefix("www.")

    /** Exact host match (ignoring a leading "www."). Deliberately NOT suffix matching: sibling subdomains must never receive each other's passwords. */
    fun matches(item: LoginItem, domain: String): Boolean = domain.isNotBlank() && norm(item.host) == norm(domain)

    /** Same site + same username => treat a new save as an update of the existing entry. */
    fun sameAccount(a: LoginItem, origin: String, username: String): Boolean =
        norm(a.host) == norm(UrlTools.hostOf(origin).orEmpty()) && a.username == username

    /** Entries whose passwords are identical (reuse), keyed by item id. */
    fun reusedIds(items: List<LoginItem>): Set<String> =
        items.filter { it.password.isNotEmpty() }.groupBy { it.password }.filterValues { it.size > 1 }.values.flatten().map { it.id }.toSet()
}
