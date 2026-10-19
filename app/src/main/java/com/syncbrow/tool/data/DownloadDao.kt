package com.syncbrow.tool.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_table ORDER BY timestamp DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity): Long

    @Update
    suspend fun updateDownload(download: DownloadEntity)

    @Query("SELECT * FROM download_table WHERE filename = :title OR :actualFilename LIKE '%' || filename ORDER BY timestamp DESC LIMIT 1")
    suspend fun findLatestMatchingDownload(title: String?, actualFilename: String): DownloadEntity?

    @Delete
    suspend fun deleteDownload(download: DownloadEntity)

    @Query("DELETE FROM download_table")
    suspend fun clearAllDownloads()
}
