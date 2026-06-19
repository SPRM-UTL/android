package com.example.android.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DispositivoDao {
    @Query("SELECT * FROM dispositivos")
    fun getAllDispositivos(): Flow<List<Dispositivo>>

    @Query("SELECT * FROM dispositivos")
    suspend fun getAllDispositivosOnce(): List<Dispositivo>

    @Query("SELECT * FROM dispositivos WHERE id = :id")
    suspend fun getDispositivoById(id: Int): Dispositivo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDispositivo(dispositivo: Dispositivo): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(dispositivos: List<Dispositivo>): List<Long>

    @Delete
    suspend fun deleteDispositivo(dispositivo: Dispositivo): Int

    @Query("DELETE FROM dispositivos WHERE id = :id")
    suspend fun deleteDispositivoById(id: Int): Int

    @Query("DELETE FROM dispositivos")
    suspend fun deleteAllDispositivos(): Int
}
