package com.example.android.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GestoDao {
    @Query("SELECT * FROM gestos")
    fun getAllGestos(): Flow<List<Gesto>>

    @Query("SELECT * FROM gestos WHERE fraseVozActivadora IS NOT NULL AND fraseVozActivadora != ''")
    suspend fun getGestosConFraseVoz(): List<Gesto>

    @Query("SELECT * FROM gestos WHERE aparatoId = :aparatoId")
    fun getGestosByDispositivo(aparatoId: Int): Flow<List<Gesto>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGesto(gesto: Gesto): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(gestos: List<Gesto>): List<Long>

    @Delete
    suspend fun deleteGesto(gesto: Gesto): Int

    @Query("DELETE FROM gestos WHERE id = :id")
    suspend fun deleteGestoById(id: Int): Int

    @Query("DELETE FROM gestos")
    suspend fun deleteAllGestos(): Int
}
