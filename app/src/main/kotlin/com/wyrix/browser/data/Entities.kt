package com.wyrix.browser.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "history", indices = [Index(value = ["url"], unique = true), Index("lastVisit"), Index("host")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val host: String,
    val lastVisit: Long,
    val visitCount: Int,
)

@Entity(tableName = "bookmark_folders", indices = [Index("parentId")])
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long?,
    val position: Int,
    val createdAt: Long,
)

@Entity(tableName = "bookmarks", indices = [Index("folderId"), Index("normUrl")])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val normUrl: String,
    val folderId: Long?,
    /** null = visible in every workspace. */
    val workspaceId: String?,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Int,
    val icon: String,
    val position: Int,
)

@Entity(tableName = "tab_groups", indices = [Index("workspaceId")])
data class TabGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Int,
    val collapsed: Boolean,
    val workspaceId: String,
    val position: Int,
)

@Entity(tableName = "tabs")
data class TabEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val workspaceId: String,
    val groupId: String?,
    val pinned: Boolean,
    val desktopMode: Boolean,
    val position: Int,
    val createdAt: Long,
    val lastAccessed: Long,
    val sessionState: String?,
)

@Entity(tableName = "shortcuts")
data class ShortcutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val color: Int,
    val position: Int,
    val workspaceId: String?,
)

object DownloadStatus {
    const val PENDING = 0
    const val RUNNING = 1
    const val SUCCESS = 2
    const val FAILED = 3
    const val PAUSED = 4
    val ACTIVE = listOf(PENDING, RUNNING, PAUSED)
}

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Android DownloadManager id; null for transfers streamed by the app (blob:/data:). */
    val dmId: Long?,
    val url: String,
    val fileName: String,
    val mime: String?,
    val status: Int,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val localUri: String?,
    val startedAt: Long,
    val reason: String? = null,
)
