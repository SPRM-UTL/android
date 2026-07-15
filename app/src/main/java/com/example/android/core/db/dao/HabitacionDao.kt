package com.example.android.core.db.dao

import com.example.android.core.db.models.*

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitacionDao {
    @Query("SELECT * FROM habitaciones WHERE skCasaId = :casaId ORDER BY nombre ASC")
    fun getHabitacionesByCasa(casaId: Int): Flow<List<Habitacion>>

    @Query("SELECT * FROM habitaciones WHERE id = :id LIMIT 1")
    suspend fun getHabitacionById(id: Int): Habitacion?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(habitaciones: List<Habitacion>): List<Long>

    @Query("DELETE FROM habitaciones")
    suspend fun deleteAllHabitaciones(): Int

    @androidx.room.Update
    suspend fun updateHabitacion(habitacion: Habitacion): Int

    @androidx.room.Delete
    suspend fun delete(habitacion: Habitacion): Int
}
