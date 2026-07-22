package com.example.android.core.db.dao

import com.example.android.core.db.models.*

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

    @Query("SELECT d.* FROM dispositivos d INNER JOIN habitaciones h ON d.sk_habitacion_id = h.id WHERE h.skCasaId = :casaId")
    fun getDispositivosByCasaId(casaId: Int): Flow<List<Dispositivo>>

    @Query("SELECT * FROM dispositivos")
    suspend fun getAllDispositivosOnce(): List<Dispositivo>

    @Query("SELECT * FROM dispositivos WHERE id = :id")
    suspend fun getDispositivoById(id: Int): Dispositivo?

    suspend fun updateEstadoEncendido(id: Int, estado: Boolean): Int {
        val d = getDispositivoById(id) ?: return 0
        insertDispositivo(d.copy(estadoEncendido = estado))
        return 1
    }

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
