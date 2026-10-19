package com.wyrix.browser

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wyrix.browser.core.BookmarkHtml
import com.wyrix.browser.data.*
import com.wyrix.browser.tabs.TabState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private lateinit var db: WyrixDatabase
    private lateinit var history: HistoryRepository
    private lateinit var bookmarks: BookmarkRepository
    private lateinit var tabs: TabRepository

    @Before fun open() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WyrixDatabase::class.java).allowMainThreadQueries().build()
        history = HistoryRepository(db.history()); bookmarks = BookmarkRepository(db.bookmarks()); tabs = TabRepository(db.tabs())
    }
    @After fun close() = db.close()

    @Test fun historyDeduplicatesByNormalizedUrlAndCountsVisits() = runBlocking {
        history.record("https://Example.com/a#x", "A", 1); history.record("https://example.com/a", "", 2)
        val all = history.observe("").first()
        assertEquals(1, all.size); assertEquals(2, all[0].visitCount); assertEquals("A", all[0].title); assertEquals(2L, all[0].lastVisit)
    }
    @Test fun historyRejectsNonWebUrls() = runBlocking {
        history.record("javascript:1", "x"); history.record("file:///x", "x")
        assertTrue(history.observe("").first().isEmpty())
    }
    @Test fun historySearchEscapesWildcards() = runBlocking {
        history.record("https://a.example/100%25", "Sale 100%"); history.record("https://b.example/", "Other")
        assertEquals(1, history.observe("100%").first().size)
        assertEquals(0, history.observe("%_%").first().size)
    }
    @Test fun historyDeleteByHostAndRange() = runBlocking {
        history.record("https://a.example/1", "1", 100); history.record("https://a.example/2", "2", 200); history.record("https://b.example/", "b", 300)
        history.deleteHost("a.example"); assertEquals(listOf("b.example"), history.observe("").first().map { it.host })
        history.clearSince(250); assertTrue(history.observe("").first().isEmpty())
    }
    @Test fun bookmarkDuplicatesDetectedAcrossFormatting() = runBlocking {
        assertTrue(bookmarks.add("a", "https://x.example/p", null, null) is AddResult.Added)
        assertTrue(bookmarks.add("a again", "https://X.example:443/p#frag", null, null) is AddResult.Duplicate)
        assertTrue(bookmarks.add("bad", "javascript:1", null, null) is AddResult.Invalid)
    }
    @Test fun folderDeletionRemovesDescendants() = runBlocking {
        val f1 = bookmarks.createFolder("one", null); val f2 = bookmarks.createFolder("two", f1)
        bookmarks.add("a", "https://a.example", f1, null); bookmarks.add("b", "https://b.example", f2, null); bookmarks.add("keep", "https://c.example", null, null)
        bookmarks.deleteFolderTree(db.bookmarks().folder(f1)!!)
        assertEquals(listOf("keep"), db.bookmarks().all().map { it.title }); assertTrue(db.bookmarks().allFolders().isEmpty())
    }
    @Test fun importExportRoundTrip() = runBlocking {
        val html = "<DL><DT><H3>F</H3><DL><DT><A HREF=\"https://one.example/\">One</A></DL><DT><A HREF=\"https://two.example/\">Two</A></DL>"
        val s = bookmarks.importParsed(BookmarkHtml.parse(html))
        assertEquals(2, s.imported)
        assertEquals(0, bookmarks.importParsed(BookmarkHtml.parse(html)).imported) // idempotent
        val exported = bookmarks.exportHtml()
        assertTrue(exported.contains("https://one.example/") && exported.contains("<H3>F</H3>"))
    }
    @Test fun bookmarkReorderSwapsPositions() = runBlocking {
        bookmarks.add("a", "https://a.example", null, null); bookmarks.add("b", "https://b.example", null, null)
        val list = bookmarks.observeIn(null).first()
        bookmarks.reorder(list, list[0].id, 1)
        assertEquals(listOf("b", "a"), bookmarks.observeIn(null).first().map { it.title })
    }
    @Test fun tabsPersistOnlyNormalAndRoundTrip() = runBlocking {
        tabs.save(listOf(TabState("n", "https://n.example", "N", showHome = false), TabState("p", "https://p.example", isPrivate = true)), mapOf("n" to "state"))
        val r = tabs.load(emptySet(), setOf("personal"))
        assertEquals(listOf("n"), r.tabs.map { it.id }); assertEquals("state", r.states["n"])
    }
}
