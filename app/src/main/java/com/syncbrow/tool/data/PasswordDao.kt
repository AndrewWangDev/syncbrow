package com.syncbrow.tool.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordDao {
    @Query("SELECT * FROM password_table ORDER BY timestamp DESC")
    fun getAllPasswords(): Flow<List<PasswordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPassword(password: PasswordEntity)

    @Update
    suspend fun updatePassword(password: PasswordEntity)

    @Delete
    suspend fun deletePassword(password: PasswordEntity)

    @Query("DELETE FROM password_table")
    suspend fun deleteAllPasswords()

    @Query("SELECT * FROM password_table WHERE siteName LIKE '%' || :siteQuery || '%' ORDER BY timestamp DESC")
    fun getPasswordsBySite(siteQuery: String): Flow<List<PasswordEntity>>
}
