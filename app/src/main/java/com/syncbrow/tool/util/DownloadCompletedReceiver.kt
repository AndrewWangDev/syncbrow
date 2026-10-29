package com.syncbrow.tool.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.syncbrow.tool.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DownloadCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (downloadId == -1L) return

            try {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = dm.query(query)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val statusIdx = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val status = if (statusIdx != -1) it.getInt(statusIdx) else -1
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            val uriIdx = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                            val uriString = if (uriIdx != -1) it.getString(uriIdx) else null
                            val titleIdx = it.getColumnIndex(DownloadManager.COLUMN_TITLE)
                            val title = if (titleIdx != -1) it.getString(titleIdx) else null

                            val uri = uriString?.let { u -> Uri.parse(u) }
                            val actualPath = uri?.path
                            val actualFilename = uri?.lastPathSegment ?: title

                            if (actualPath != null && actualFilename != null) {
                                Log.d("DownloadReceiver", "Download finished: id=$downloadId, path=$actualPath, name=$actualFilename")
                                val pendingResult = goAsync()
                                val db = AppDatabase.getDatabase(context)
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        val match = db.downloadDao().findLatestMatchingDownload(title, actualFilename)
                                        if (match != null) {
                                            db.downloadDao().updateDownload(
                                                match.copy(
                                                    localPath = actualPath,
                                                    filename = actualFilename
                                                )
                                            )
                                            Log.d("DownloadReceiver", "Updated download record id=${match.id} with real path: $actualPath")
                                        }
                                    } catch (e: Exception) {
                                        Log.e("DownloadReceiver", "Error updating download record", e)
                                    } finally {
                                        pendingResult.finish()
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DownloadReceiver", "Error querying DownloadManager", e)
            }
        }
    }
}
