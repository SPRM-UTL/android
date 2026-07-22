package com.example.android.core.db.dao

import com.example.android.core.db.models.*

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GestoDetalleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetalle(detalle: GestoDetalle): Long

    @Query("SELECT * FROM gesto_detalle WHERE gestoId = :gestoId LIMIT 1")
    suspend fun getDetallePorGestoId(gestoId: Int): GestoDetalle?

    @Query("DELETE FROM gesto_detalle WHERE gestoId = :gestoId")
    suspend fun deleteDetallePorGestoId(gestoId: Int): Int
}
