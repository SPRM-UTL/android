package com.example.android.core.db.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "combos")
data class ComboEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val activadorJson: String? = null,
    val pasosJson: String = "[]",
    val accionVinculada: String? = null,
    val aparatoId: Int? = null,
    val accionEncendido: Boolean? = null,
    val contactoOutlet: Int? = null,
    val backendGestoId: Int? = null,
    val icono: String? = "lucide_star",
    val fraseVozActivadora: String? = null,
    val userId: Int = -1
)
