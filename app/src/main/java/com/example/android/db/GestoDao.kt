package com.example.android.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface GestoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertGesto(gesto: Gesto): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(gestos: List<Gesto>)

    @Update
    fun updateGesto(gesto: Gesto)

    @Query("SELECT * FROM gestos WHERE sk_gesto_id = :gestoId")
    fun getGestoById(gestoId: Long): Gesto?

    // 🌟 AÑADIDO: Retorna los gestos que tienen una frase de voz configurada
    @Query("SELECT * FROM gestos WHERE frase_voz_activadora IS NOT NULL AND frase_voz_activadora != ''")
    fun getGestosConFraseVoz(): List<Gesto>

    @Query("DELETE FROM gestos")
    fun deleteAllGestos()
}