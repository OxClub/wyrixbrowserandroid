package com.wyrix.browser.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        HistoryEntity::class, FolderEntity::class, BookmarkEntity::class, WorkspaceEntity::class,
        TabGroupEntity::class, TabEntity::class, ShortcutEntity::class, DownloadEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class WyrixDatabase : RoomDatabase() {
    abstract fun history(): HistoryDao
    abstract fun bookmarks(): BookmarkDao
    abstract fun tabs(): TabDao
    abstract fun organization(): OrganizationDao
    abstract fun downloads(): DownloadDao

    companion object {
        const val NAME = "wyrix.db"
        // Future schema changes MUST add a Migration here (and a test in MigrationTest); we never
        // use destructive migration because that would silently erase a user's bookmarks.
        fun build(context: Context): WyrixDatabase =
            Room.databaseBuilder(context.applicationContext, WyrixDatabase::class.java, NAME).build()
    }
}
