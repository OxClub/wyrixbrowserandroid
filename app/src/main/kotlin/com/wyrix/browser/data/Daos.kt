package com.wyrix.browser.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class HistoryDao {
    @Query("SELECT * FROM history WHERE url = :url LIMIT 1")
    abstract suspend fun byUrl(url: String): HistoryEntity?

    @Insert abstract suspend fun insert(e: HistoryEntity): Long
    @Update abstract suspend fun update(e: HistoryEntity)

    /** One row per normalized URL; repeated visits bump the count instead of adding rows. */
    @Transaction
    open suspend fun recordVisit(url: String, title: String, host: String, now: Long) {
        val existing = byUrl(url)
        if (existing == null) insert(HistoryEntity(url = url, title = title, host = host, lastVisit = now, visitCount = 1))
        else update(existing.copy(title = title.ifBlank { existing.title }, lastVisit = now, visitCount = existing.visitCount + 1))
    }

    @Query("UPDATE history SET title = :title WHERE url = :url AND :title != ''")
    abstract suspend fun updateTitle(url: String, title: String)

    @Query("SELECT * FROM history WHERE url LIKE :pattern ESCAPE '\\' OR title LIKE :pattern ESCAPE '\\' ORDER BY lastVisit DESC LIMIT :limit")
    abstract fun observe(pattern: String, limit: Int): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE url LIKE :pattern ESCAPE '\\' OR title LIKE :pattern ESCAPE '\\' ORDER BY visitCount DESC, lastVisit DESC LIMIT :limit")
    abstract suspend fun suggest(pattern: String, limit: Int): List<HistoryEntity>

    @Query("SELECT * FROM history ORDER BY visitCount DESC, lastVisit DESC LIMIT :limit")
    abstract fun observeFrequent(limit: Int): Flow<List<HistoryEntity>>

    @Query("DELETE FROM history WHERE id = :id") abstract suspend fun deleteById(id: Long)
    @Query("DELETE FROM history WHERE host = :host") abstract suspend fun deleteByHost(host: String)
    @Query("DELETE FROM history WHERE lastVisit >= :since") abstract suspend fun deleteSince(since: Long)
    @Query("DELETE FROM history") abstract suspend fun clear()
    @Query("SELECT COUNT(*) FROM history") abstract suspend fun count(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun restore(e: HistoryEntity): Long
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE (folderId IS :folderId) ORDER BY position ASC, id ASC")
    fun observeIn(folderId: Long?): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE url LIKE :pattern ESCAPE '\\' OR title LIKE :pattern ESCAPE '\\' ORDER BY title COLLATE NOCASE LIMIT :limit")
    suspend fun search(pattern: String, limit: Int): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks") suspend fun all(): List<BookmarkEntity>
    @Query("SELECT * FROM bookmarks WHERE normUrl = :norm LIMIT 1") suspend fun findByNorm(norm: String): BookmarkEntity?
    @Query("SELECT COALESCE(MAX(position), -1) FROM bookmarks WHERE folderId IS :folderId") suspend fun maxPosition(folderId: Long?): Int
    @Insert suspend fun insert(e: BookmarkEntity): Long
    @Update suspend fun update(e: BookmarkEntity)
    @Update suspend fun updateAll(e: List<BookmarkEntity>)
    @Delete suspend fun delete(e: BookmarkEntity)
    @Query("DELETE FROM bookmarks WHERE folderId = :folderId") suspend fun deleteInFolder(folderId: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restore(e: BookmarkEntity): Long

    @Query("SELECT * FROM bookmark_folders WHERE (parentId IS :parentId) ORDER BY position ASC, id ASC")
    fun observeFolders(parentId: Long?): Flow<List<FolderEntity>>
    @Query("SELECT * FROM bookmark_folders") suspend fun allFolders(): List<FolderEntity>
    @Query("SELECT * FROM bookmark_folders WHERE id = :id") suspend fun folder(id: Long): FolderEntity?
    @Insert suspend fun insertFolder(f: FolderEntity): Long
    @Update suspend fun updateFolder(f: FolderEntity)
    @Delete suspend fun deleteFolder(f: FolderEntity)
}

@Dao
abstract class TabDao {
    @Query("SELECT * FROM tabs ORDER BY position ASC") abstract suspend fun all(): List<TabEntity>
    @Query("DELETE FROM tabs") abstract suspend fun clear()
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertAll(tabs: List<TabEntity>)

    @Transaction
    open suspend fun replaceAll(tabs: List<TabEntity>) {
        clear()
        insertAll(tabs)
    }
}

@Dao
interface OrganizationDao {
    @Query("SELECT * FROM workspaces ORDER BY position ASC") fun observeWorkspaces(): Flow<List<WorkspaceEntity>>
    @Query("SELECT COUNT(*) FROM workspaces") suspend fun workspaceCount(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertWorkspace(w: WorkspaceEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertWorkspaces(w: List<WorkspaceEntity>)
    @Query("DELETE FROM workspaces WHERE id = :id") suspend fun deleteWorkspace(id: String)
    @Query("SELECT COALESCE(MAX(position), -1) FROM workspaces") suspend fun maxWorkspacePosition(): Int

    @Query("SELECT * FROM tab_groups ORDER BY position ASC") fun observeGroups(): Flow<List<TabGroupEntity>>
    @Query("SELECT * FROM tab_groups") suspend fun allGroups(): List<TabGroupEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertGroup(g: TabGroupEntity)
    @Query("DELETE FROM tab_groups WHERE id = :id") suspend fun deleteGroup(id: String)
    @Query("DELETE FROM tab_groups WHERE workspaceId = :workspaceId") suspend fun deleteGroupsIn(workspaceId: String)
    @Query("SELECT COALESCE(MAX(position), -1) FROM tab_groups") suspend fun maxGroupPosition(): Int

    @Query("SELECT * FROM shortcuts ORDER BY position ASC, id ASC") fun observeShortcuts(): Flow<List<ShortcutEntity>>
    @Query("SELECT COALESCE(MAX(position), -1) FROM shortcuts") suspend fun maxShortcutPosition(): Int
    @Insert suspend fun insertShortcut(s: ShortcutEntity): Long
    @Update suspend fun updateShortcut(s: ShortcutEntity)
    @Update suspend fun updateShortcuts(s: List<ShortcutEntity>)
    @Delete suspend fun deleteShortcut(s: ShortcutEntity)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY startedAt DESC") fun observe(): Flow<List<DownloadEntity>>
    @Query("SELECT * FROM downloads WHERE status IN (0, 1, 4)") suspend fun active(): List<DownloadEntity>
    @Query("SELECT * FROM downloads WHERE id = :id") suspend fun byId(id: Long): DownloadEntity?
    @Insert suspend fun insert(e: DownloadEntity): Long
    @Update suspend fun update(e: DownloadEntity)
    @Query("DELETE FROM downloads WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM downloads WHERE status IN (2, 3)") suspend fun clearFinished()
}
