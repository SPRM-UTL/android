package com.example.android.core.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "casas")
data class Casa(
    @PrimaryKey
    @SerializedName("sk_casa_id")
    val id: Int,

    @SerializedName("nombre_casa")
    val nombre: String?,
    
    @SerializedName("sk_usuario_id")
    val skUsuarioId: Int?
)
