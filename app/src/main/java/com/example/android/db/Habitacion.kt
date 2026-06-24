package com.example.android.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "habitaciones")
data class Habitacion(
    @PrimaryKey
    @SerializedName("sk_habitacion_id")
    val id: Int,

    @SerializedName("nombre_habitacion")
    val nombre: String?,

    @SerializedName("sk_casa_id")
    val skCasaId: Int?
)
