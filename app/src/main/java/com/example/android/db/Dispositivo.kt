package com.example.android.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "dispositivos")
data class Dispositivo(
    @PrimaryKey
    @SerializedName("sk_aparato_id")
    val id: Int,

    @SerializedName("nombre_aparato")
    val nombre: String?,

    @SerializedName("tipo_aparato")
    val tipo: String?,

    @SerializedName("accion_nombre")
    val accion: String?,

    @SerializedName("comando_bluetooth")
    val comandoBluetooth: String?,

    @SerializedName("icono")
    val icono: String?
)
