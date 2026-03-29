package com.syncbrow.tool.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_table")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val filename: String,
    val timestamp: Long,
    val localPath: String? = null
)
