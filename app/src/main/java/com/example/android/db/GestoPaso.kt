package com.example.android.db

import com.google.gson.annotations.SerializedName

data class GestoPaso(
    @SerializedName("sk_gesto_paso_id")
    val id: Int = 0,

    @SerializedName("orden")
    val orden: Int,

    @SerializedName("es_activador")
    val esActivador: Boolean,

    @SerializedName("nombre_gesto")
    val nombreGesto: String,

    @SerializedName("mano_objetivo")
    val manoObjetivo: String,

    @SerializedName("cuadros_requeridos")
    val cuadrosRequeridos: Int
)
