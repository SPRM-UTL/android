package com.example.android.core.db.dao

import com.example.android.core.db.models.*

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface GestoDetalleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetalle(detalle: GestoDetalle): Long

    @Update
    suspend fun updateDetalle(detalle: GestoDetalle)

    @Query("SELECT * FROM gesto_detalle WHERE sk_gesto_id = :gestoId LIMIT 1")
    suspend fun getDetallePorGestoId(gestoId: Int): GestoDetalle?

    @Query("DELETE FROM gesto_detalle WHERE sk_gesto_id = :gestoId")
    suspend fun deleteDetallePorGestoId(gestoId: Int): Int
}
