package com.wyrix.browser.data

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.wyrix.browser.core.FilenameSanitizer
import com.wyrix.browser.core.UrlTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

data class DownloadRequest(
    val url: String,
    val fileNameHint: String?,
    val mime: String?,
    val contentLength: Long,
    /** Response body handed over by the engine; only used for non-HTTP(S) transfers (blob:, data:). */
    val body: InputStream?,
    val record: Boolean,
)

sealed interface DownloadOutcome {
    data class Started(val fileName: String) : DownloadOutcome
    data class Unsupported(val reason: String) : DownloadOutcome
}

/**
 * HTTP(S) transfers go through Android's DownloadManager. Known limitation: DownloadManager
 * re-requests the URL itself, so downloads that depend on cookies, POST bodies or custom headers
 * may fail; those are reported as failed with a reason rather than hidden.
 * blob:/data: transfers are streamed from the engine-provided body into MediaStore (API 29+) or the
 * app's external files directory (API 28). Pause/resume is intentionally not exposed.
 */
class DownloadCoordinator(
    private val context: Context,
    private val dao: DownloadDao,
    private val scope: CoroutineScope,
) {
    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var pollJob: Job? = null

    suspend fun enqueue(req: DownloadRequest): DownloadOutcome {
        val name = FilenameSanitizer.sanitize(req.fileNameHint, req.url, req.mime)
        val scheme = UrlTools.scheme(req.url)
        return when (scheme) {
            "http", "https" -> { req.body?.closeQuietly(); enqueueViaManager(req, name) }
            "blob", "data" -> streamToDisk(req, name)
            else -> { req.body?.closeQuietly(); DownloadOutcome.Unsupported("Downloads from \"$scheme:\" addresses aren't supported.") }
        }
    }

    private suspend fun enqueueViaManager(req: DownloadRequest, name: String): DownloadOutcome = withContext(Dispatchers.IO) {
        try {
            val r = DownloadManager.Request(Uri.parse(req.url))
                .setTitle(name)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            req.mime?.takeIf { it.isNotBlank() }?.let { r.setMimeType(it.substringBefore(';')) }
            if (Build.VERSION.SDK_INT >= 29) r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            else r.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name) // no storage permission on API 28
            val dmId = dm.enqueue(r)
            if (req.record) {
                dao.insert(DownloadEntity(dmId = dmId, url = req.url, fileName = name, mime = req.mime, status = DownloadStatus.PENDING,
                    totalBytes = req.contentLength, downloadedBytes = 0, localUri = null, startedAt = System.currentTimeMillis()))
                ensurePolling()
            }
            DownloadOutcome.Started(name)
        } catch (e: Exception) {
            DownloadOutcome.Unsupported("The system download manager refused this download.")
        }
    }

    private suspend fun streamToDisk(req: DownloadRequest, name: String): DownloadOutcome {
        val body = req.body ?: return DownloadOutcome.Unsupported("This download has no data available to save.")
        val rowId = if (req.record) dao.insert(DownloadEntity(dmId = null, url = "blob/data", fileName = name, mime = req.mime,
            status = DownloadStatus.RUNNING, totalBytes = req.contentLength, downloadedBytes = 0, localUri = null,
            startedAt = System.currentTimeMillis())) else -1L
        scope.launch(Dispatchers.IO) {
            try {
                val (uri, finish) = openOutput(name, req.mime)
                var total = 0L
                body.use { input -> context.contentResolver.openOutputStream(uri)!!.use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); total += n
                    }
                } }
                finish()
                if (rowId >= 0) dao.byId(rowId)?.let { dao.update(it.copy(status = DownloadStatus.SUCCESS, downloadedBytes = total, totalBytes = total, localUri = uri.toString())) }
            } catch (e: Exception) {
                if (rowId >= 0) dao.byId(rowId)?.let { dao.update(it.copy(status = DownloadStatus.FAILED, reason = "Could not write the file (storage full or access denied).")) }
            }
        }
        return DownloadOutcome.Started(name)
    }

    private fun openOutput(name: String, mime: String?): Pair<Uri, () -> Unit> {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                mime?.takeIf { it.isNotBlank() }?.let { put(MediaStore.Downloads.MIME_TYPE, it.substringBefore(';')) }
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("insert failed")
            return uri to { context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null); Unit }
        }
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: error("no storage")
        dir.mkdirs()
        var f = File(dir, name); var i = 1
        while (f.exists()) { f = File(dir, "${name.substringBeforeLast('.', name)}($i)${name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }}"); i++ }
        return Uri.fromFile(f) to {}
    }

    /** Polls DownloadManager while transfers are active; ends itself when none remain. */
    fun ensurePolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch(Dispatchers.IO) {
            while (true) {
                val active = dao.active().filter { it.dmId != null }
                if (active.isEmpty()) break
                refresh(active)
                delay(1000)
            }
        }
    }

    private suspend fun refresh(rows: List<DownloadEntity>) {
        val ids = rows.mapNotNull { it.dmId }.toLongArray()
        dm.query(DownloadManager.Query().setFilterById(*ids))?.use { c ->
            val iId = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val iSt = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            val iDone = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val iTotal = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val iReason = c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
            val iUri = c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
            val seen = HashSet<Long>()
            while (c.moveToNext()) {
                val dmId = c.getLong(iId); seen += dmId
                val row = rows.firstOrNull { it.dmId == dmId } ?: continue
                val status = when (c.getInt(iSt)) {
                    DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.SUCCESS
                    DownloadManager.STATUS_FAILED -> DownloadStatus.FAILED
                    DownloadManager.STATUS_PAUSED -> DownloadStatus.PAUSED
                    DownloadManager.STATUS_RUNNING -> DownloadStatus.RUNNING
                    else -> DownloadStatus.PENDING
                }
                val reason = if (status == DownloadStatus.FAILED) describeReason(c.getInt(iReason)) else null
                dao.update(row.copy(status = status, downloadedBytes = c.getLong(iDone), totalBytes = c.getLong(iTotal).takeIf { it > 0 } ?: row.totalBytes,
                    localUri = c.getString(iUri), reason = reason))
            }
            // Entries removed outside the app (e.g. from the system Downloads UI) must not poll forever.
            rows.filter { it.dmId !in seen }.forEach { dao.update(it.copy(status = DownloadStatus.FAILED, reason = "The download was removed.")) }
        }
    }

    private fun describeReason(code: Int): String = when (code) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough storage space."
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage is unavailable."
        DownloadManager.ERROR_CANNOT_RESUME -> "The transfer could not be resumed."
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "A file with that name already exists."
        DownloadManager.ERROR_FILE_ERROR -> "A file error occurred."
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "A network data error occurred."
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects."
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "The server refused the request (it may require sign-in; cookies aren't forwarded)."
        else -> "The download failed (code $code)."
    }

    fun openIntent(e: DownloadEntity): Intent? {
        if (e.status != DownloadStatus.SUCCESS) return null
        val uri = e.dmId?.let { dm.getUriForDownloadedFile(it) } ?: e.localUri?.let { Uri.parse(it) } ?: return null
        val mime = e.dmId?.let { dm.getMimeTypeForDownloadedFile(it) } ?: e.mime ?: "*/*"
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun shareIntent(e: DownloadEntity): Intent? {
        if (e.status != DownloadStatus.SUCCESS) return null
        val uri = e.dmId?.let { dm.getUriForDownloadedFile(it) } ?: e.localUri?.let { Uri.parse(it) } ?: return null
        val mime = e.dmId?.let { dm.getMimeTypeForDownloadedFile(it) } ?: e.mime ?: "*/*"
        return Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null)
    }

    /** Retry is only valid for HTTP(S) transfers; blob:/data: sources no longer exist after the page is gone. */
    suspend fun retry(e: DownloadEntity): DownloadOutcome {
        if (!UrlTools.isWebUrl(e.url)) return DownloadOutcome.Unsupported("This download can't be retried; start it again from the page.")
        dao.delete(e.id)
        return enqueueViaManager(DownloadRequest(e.url, e.fileName, e.mime, e.totalBytes, null, true), e.fileName)
    }

    /** Removes the list entry; with [deleteFile] also removes the downloaded file. */
    suspend fun remove(e: DownloadEntity, deleteFile: Boolean) = withContext(Dispatchers.IO) {
        if (deleteFile) {
            runCatching { e.dmId?.let { dm.remove(it) } ?: e.localUri?.let { u ->
                val uri = Uri.parse(u)
                if (uri.scheme == "file") File(uri.path!!).delete() else context.contentResolver.delete(uri, null, null)
            } }
        } else if (e.status in DownloadStatus.ACTIVE) {
            runCatching { e.dmId?.let { dm.remove(it) } }
        }
        dao.delete(e.id)
    }

    suspend fun clearFinished() = dao.clearFinished()
    suspend fun resumePollingIfNeeded() { if (dao.active().any { it.dmId != null }) ensurePolling() }
}

private fun InputStream.closeQuietly() { runCatching { close() } }
