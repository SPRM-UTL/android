package com.example.android.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usuarios")
data class Usuario(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nombreUsuario: String,
    val contrasena: String
)