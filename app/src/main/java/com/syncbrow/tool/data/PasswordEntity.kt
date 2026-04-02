package com.syncbrow.tool.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "password_table")
data class PasswordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val siteName: String,
    val username: String,
    val password: String,
    val timestamp: Long = System.currentTimeMillis()
)
