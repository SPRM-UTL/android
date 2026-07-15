package com.example.android.core.db.dao

import com.example.android.core.db.models.*

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

    @Query("SELECT * FROM casas WHERE id = :id LIMIT 1")
    suspend fun getCasaById(id: Int): Casa?

    @androidx.room.Update
    suspend fun updateCasa(casa: Casa): Int

    @androidx.room.Delete
    suspend fun delete(casa: Casa): Int
}
