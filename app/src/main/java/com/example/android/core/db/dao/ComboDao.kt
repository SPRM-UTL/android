package com.example.android.core.db.dao

import com.example.android.core.db.models.ComboEntity
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ComboDao {
    @Query("SELECT * FROM combos WHERE userId = :userId")
    suspend fun getCombosByUserId(userId: Int): List<ComboEntity>

    @Query("SELECT * FROM combos WHERE id = :comboId")
    suspend fun getComboById(comboId: String): ComboEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCombo(combo: ComboEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(combos: List<ComboEntity>): List<Long>

    @Delete
    suspend fun deleteCombo(combo: ComboEntity): Int

    @Query("DELETE FROM combos WHERE id = :comboId")
    suspend fun deleteComboById(comboId: String): Int

    @Query("DELETE FROM combos WHERE userId = :userId")
    suspend fun deleteAllCombosByUserId(userId: Int): Int
}
