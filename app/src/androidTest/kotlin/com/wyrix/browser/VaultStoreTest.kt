package com.wyrix.browser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wyrix.browser.data.VaultState
import com.wyrix.browser.data.VaultStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises the real Android Keystore, so it only runs on a device/emulator. */
@RunWith(AndroidJUnit4::class)
class VaultStoreTest {
    private lateinit var ctx: Context

    @Before fun setUp() { ctx = ApplicationProvider.getApplicationContext(); runBlocking { VaultStore(ctx).resetEverything() } }
    @After fun tearDown() { runBlocking { VaultStore(ctx).resetEverything() } }

    @Test fun savedLoginsSurviveReopenAndAreEncryptedOnDisk() = runBlocking {
        val v1 = VaultStore(ctx)
        assertTrue(v1.upsert("https://example.com", null, "bob", "s3cret-Passw0rd!"))
        val raw = File(ctx.filesDir, "vault.bin").readBytes()
        assertFalse("plaintext leaked into the vault file", String(raw, Charsets.ISO_8859_1).contains("s3cret"))
        val v2 = VaultStore(ctx)               // fresh instance: must decrypt from disk
        val found = v2.findForHost("www.example.com")
        assertEquals(1, found.size); assertEquals("s3cret-Passw0rd!", found[0].password)
        assertTrue(v2.findForHost("evil.example.com").isEmpty())
    }
    @Test fun sameSiteAndUsernameUpdatesInsteadOfDuplicating() = runBlocking {
        val v = VaultStore(ctx)
        v.upsert("https://example.com", null, "bob", "one"); v.upsert("https://example.com/login", null, "bob", "two"); v.upsert("https://example.com", null, "alice", "three")
        val all = v.all()
        assertEquals(2, all.size); assertEquals("two", all.first { it.username == "bob" }.password)
    }
    @Test fun tamperedFileIsReportedNotWiped() = runBlocking {
        VaultStore(ctx).upsert("https://example.com", null, "bob", "pw")
        val f = File(ctx.filesDir, "vault.bin"); val bytes = f.readBytes(); bytes[bytes.size - 1] = (bytes.last().toInt() xor 0x55).toByte(); f.writeBytes(bytes)
        val v = VaultStore(ctx); v.refresh()
        assertTrue(v.state.value is VaultState.Unreadable)
        assertFalse("a failed write must not overwrite an unreadable vault", v.upsert("https://other.example", null, "x", "y"))
        assertTrue(f.exists())
    }
    @Test fun importAndExportRoundTrip() = runBlocking {
        val v = VaultStore(ctx)
        val csv = "name,url,username,password,note\nx,https://a.example/login,u1,p1,n1\nx,https://b.example,u2,p\"2,\n"
        val parsed = com.wyrix.browser.core.LoginCsv.parse(csv)
        assertEquals(2 to 0, v.importCsv(parsed.logins))
        assertEquals(0 to 2, v.importCsv(parsed.logins))
        val out = com.wyrix.browser.core.LoginCsv.parse(v.exportCsv()).logins
        assertEquals(setOf("p1", "p\"2"), out.map { it.password }.toSet())
    }
}
