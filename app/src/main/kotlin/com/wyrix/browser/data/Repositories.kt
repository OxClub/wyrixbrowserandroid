package com.wyrix.browser.data

import com.wyrix.browser.core.BookmarkHtml
import com.wyrix.browser.core.ExportFolder
import com.wyrix.browser.core.ExportItem
import com.wyrix.browser.core.ParseResult
import com.wyrix.browser.core.UrlTools
import com.wyrix.browser.tabs.TabState
import com.wyrix.browser.tabs.Workspaces
import kotlinx.coroutines.flow.Flow

internal fun likePattern(q: String): String =
    "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

class HistoryRepository(private val dao: HistoryDao) {
    suspend fun record(url: String, title: String, now: Long = System.currentTimeMillis()) {
        if (!UrlTools.isWebUrl(url)) return
        val norm = UrlTools.normalize(url)
        val host = UrlTools.hostOf(norm) ?: return
        dao.recordVisit(norm, title.take(300), host, now)
    }
    suspend fun updateTitle(url: String, title: String) = dao.updateTitle(UrlTools.normalize(url), title.take(300))
    fun observe(query: String, limit: Int = 500): Flow<List<HistoryEntity>> = dao.observe(likePattern(query), limit)
    fun frequent(limit: Int): Flow<List<HistoryEntity>> = dao.observeFrequent(limit)
    suspend fun suggest(q: String, limit: Int) = if (q.isBlank()) emptyList() else dao.suggest(likePattern(q), limit)
    suspend fun delete(e: HistoryEntity) = dao.deleteById(e.id)
    suspend fun restore(e: HistoryEntity) { dao.restore(e) }
    suspend fun deleteHost(host: String) = dao.deleteByHost(host)
    /** [sinceMillis] = 0 clears everything. */
    suspend fun clearSince(sinceMillis: Long) = if (sinceMillis <= 0) dao.clear() else dao.deleteSince(sinceMillis)
}

sealed interface AddResult {
    data class Added(val id: Long) : AddResult
    data class Duplicate(val existing: BookmarkEntity) : AddResult
    data object Invalid : AddResult
}

data class ImportSummary(val imported: Int, val duplicates: Int, val skipped: Int, val truncated: Boolean)

class BookmarkRepository(private val dao: BookmarkDao) {
    fun observeIn(folderId: Long?) = dao.observeIn(folderId)
    fun observeFolders(parentId: Long?) = dao.observeFolders(parentId)
    fun recent(limit: Int) = dao.observeRecent(limit)
    suspend fun search(q: String, limit: Int = 100) = if (q.isBlank()) emptyList() else dao.search(likePattern(q), limit)
    suspend fun find(url: String): BookmarkEntity? = dao.findByNorm(UrlTools.normalize(url))
    suspend fun folders(): List<FolderEntity> = dao.allFolders()

    suspend fun add(title: String, url: String, folderId: Long?, workspaceId: String?, now: Long = System.currentTimeMillis()): AddResult {
        if (!UrlTools.isWebUrl(url)) return AddResult.Invalid
        val norm = UrlTools.normalize(url)
        dao.findByNorm(norm)?.let { return AddResult.Duplicate(it) }
        val pos = dao.maxPosition(folderId) + 1
        val t = title.trim().ifEmpty { UrlTools.hostOf(url).orEmpty() }.take(300)
        return AddResult.Added(dao.insert(BookmarkEntity(0, t, url, norm, folderId, workspaceId, pos, now, now)))
    }

    suspend fun addAll(items: List<Pair<String, String>>, workspaceId: String?): Int =
        items.count { (t, u) -> add(t, u, null, workspaceId) is AddResult.Added }

    suspend fun update(e: BookmarkEntity) = dao.update(e.copy(updatedAt = System.currentTimeMillis(), normUrl = UrlTools.normalize(e.url)))
    suspend fun delete(e: BookmarkEntity) = dao.delete(e)
    suspend fun restore(e: BookmarkEntity) { dao.restore(e) }
    suspend fun move(e: BookmarkEntity, folderId: Long?) =
        dao.update(e.copy(folderId = folderId, position = dao.maxPosition(folderId) + 1, updatedAt = System.currentTimeMillis()))

    /** Swaps position with the neighbour in [siblings] (accessible alternative to drag reorder). */
    suspend fun reorder(siblings: List<BookmarkEntity>, id: Long, delta: Int) {
        val i = siblings.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in siblings.indices) return
        val list = siblings.toMutableList()
        list.add(j, list.removeAt(i))
        dao.updateAll(list.mapIndexed { idx, b -> b.copy(position = idx) })
    }

    suspend fun createFolder(name: String, parentId: Long?): Long =
        dao.insertFolder(FolderEntity(name = name.trim().take(100).ifEmpty { "Folder" }, parentId = parentId, position = 0, createdAt = System.currentTimeMillis()))
    suspend fun renameFolder(f: FolderEntity, name: String) = dao.updateFolder(f.copy(name = name.trim().take(100).ifEmpty { f.name }))

    suspend fun deleteFolderTree(folder: FolderEntity) {
        val all = dao.allFolders()
        val doomed = LinkedHashSet<Long>()
        fun collect(id: Long) { if (doomed.add(id)) all.filter { it.parentId == id }.forEach { collect(it.id) } }
        collect(folder.id)
        for (id in doomed) {
            dao.deleteInFolder(id)
            all.firstOrNull { it.id == id }?.let { dao.deleteFolder(it) }
        }
    }

    suspend fun importParsed(parsed: ParseResult): ImportSummary {
        val folderIds = HashMap<List<String>, Long?>()
        folderIds[emptyList()] = null
        suspend fun folderFor(path: List<String>): Long? {
            folderIds[path]?.let { return it }
            if (path.isEmpty()) return null
            val parent = folderFor(path.dropLast(1))
            val existing = dao.allFolders().firstOrNull { it.parentId == parent && it.name == path.last() }
            val id = existing?.id ?: createFolder(path.last(), parent)
            folderIds[path] = id
            return id
        }
        var imported = 0; var dups = 0
        for (b in parsed.bookmarks) {
            val folder = folderFor(b.folderPath)
            when (add(b.title, b.url, folder, null, (b.addDate?.times(1000)) ?: System.currentTimeMillis())) {
                is AddResult.Added -> imported++
                is AddResult.Duplicate -> dups++
                AddResult.Invalid -> {}
            }
        }
        return ImportSummary(imported, dups, parsed.skipped, parsed.truncated)
    }

    suspend fun exportHtml(): String {
        val folders = dao.allFolders()
        val items = dao.all()
        fun build(f: FolderEntity?): ExportFolder {
            val id = f?.id
            return ExportFolder(
                name = f?.name ?: "Bookmarks",
                folders = folders.filter { it.parentId == id }.sortedBy { it.position }.map { build(it) },
                items = items.filter { it.folderId == id }.sortedBy { it.position }
                    .map { ExportItem(it.title, it.url, it.createdAt / 1000) },
            )
        }
        return BookmarkHtml.export(build(null))
    }
}

class OrganizationRepository(private val dao: OrganizationDao) {
    val workspaces = dao.observeWorkspaces()
    val groups = dao.observeGroups()
    val shortcuts = dao.observeShortcuts()

    suspend fun ensureDefaults() {
        if (dao.workspaceCount() > 0) return
        dao.insertWorkspaces(
            listOf(
                WorkspaceEntity(Workspaces.PERSONAL, "Personal", 0xFF5B8CFF.toInt(), "P", 0),
                WorkspaceEntity(Workspaces.WORK, "Work", 0xFF34D399.toInt(), "W", 1),
                WorkspaceEntity(Workspaces.STUDY, "Study", 0xFFFB923C.toInt(), "S", 2),
            ),
        )
    }
    suspend fun allGroupIds(): Set<String> = dao.allGroups().map { it.id }.toSet()

    suspend fun saveWorkspace(w: WorkspaceEntity) = dao.upsertWorkspace(w)
    suspend fun newWorkspace(name: String, color: Int): WorkspaceEntity {
        val ws = WorkspaceEntity(java.util.UUID.randomUUID().toString(), name.trim().take(40).ifEmpty { "Space" }, color,
            UrlTools.initialFor(name), dao.maxWorkspacePosition() + 1)
        dao.upsertWorkspace(ws); return ws
    }
    suspend fun deleteWorkspace(id: String) { dao.deleteGroupsIn(id); dao.deleteWorkspace(id) }

    suspend fun newGroup(name: String, color: Int, workspaceId: String): TabGroupEntity {
        val g = TabGroupEntity(java.util.UUID.randomUUID().toString(), name.trim().take(40).ifEmpty { "Group" }, color, false, workspaceId, dao.maxGroupPosition() + 1)
        dao.upsertGroup(g); return g
    }
    suspend fun saveGroup(g: TabGroupEntity) = dao.upsertGroup(g)
    suspend fun deleteGroup(id: String) = dao.deleteGroup(id)

    suspend fun addShortcut(title: String, url: String, color: Int) {
        if (!UrlTools.isWebUrl(url)) return
        dao.insertShortcut(ShortcutEntity(title = title.trim().take(40).ifEmpty { UrlTools.hostOf(url).orEmpty() }, url = url, color = color, position = dao.maxShortcutPosition() + 1, workspaceId = null))
    }
    suspend fun updateShortcut(s: ShortcutEntity) = dao.updateShortcut(s)
    suspend fun deleteShortcut(s: ShortcutEntity) = dao.deleteShortcut(s)
    suspend fun moveShortcut(all: List<ShortcutEntity>, id: Long, delta: Int) {
        val i = all.indexOfFirst { it.id == id }; val j = i + delta
        if (i < 0 || j !in all.indices) return
        val l = all.toMutableList(); l.add(j, l.removeAt(i))
        dao.updateShortcuts(l.mapIndexed { idx, s -> s.copy(position = idx) })
    }
}

class TabRepository(private val dao: TabDao) {
    suspend fun load(validGroups: Set<String>, validWorkspaces: Set<String>): TabMapper.Restored =
        TabMapper.restore(dao.all(), validGroups, validWorkspaces)

    /** Persists normal tabs only; callers must never pass private tabs. */
    suspend fun save(tabs: List<TabState>, states: Map<String, String>) {
        val rows = tabs.filter { !it.isPrivate }.mapIndexed { i, t -> TabMapper.toEntity(t, i, states[t.id]) }
        dao.replaceAll(rows)
    }
}
