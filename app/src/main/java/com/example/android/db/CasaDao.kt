package com.example.android.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CasaDao {
    @Query("SELECT * FROM casas ORDER BY nombre ASC")
    fun getAllCasas(): Flow<List<Casa>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(casas: List<Casa>): List<Long>

    @Query("DELETE FROM casas")
    suspend fun deleteAllCasas(): Int
}
