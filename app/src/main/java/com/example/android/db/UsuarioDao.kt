package com.example.android.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface UsuarioDao {

    @Insert
    suspend fun insertarUsuario(usuario: Usuario): Long

    @Query("SELECT * FROM usuarios WHERE nombreUsuario = :usuario AND contrasena = :pass LIMIT 1")
    suspend fun login(usuario: String, pass: String): Usuario?

    @Query("SELECT COUNT(*) FROM usuarios")
    suspend fun contarUsuarios(): Int
}