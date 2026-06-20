package com.example.android.db

import com.google.gson.annotations.SerializedName

data class AparatoTipo(
    @SerializedName("sk_aparato_tipo_id")
    val id: Int,
    @SerializedName("nombre_tipo")
    val nombreTipo: String,
    @SerializedName("icono")
    val icono: String?,
    @SerializedName("es_asistente")
    val esAsistente: Boolean,
    @SerializedName("soporta_bluetooth")
    val soportaBluetooth: Boolean,
    @SerializedName("soporta_wifi")
    val soportaWifi: Boolean,
    @SerializedName("orden")
    val orden: Int,
    @SerializedName("palabras_clave_busqueda")
    val palabrasClaveBusqueda: String?,
    @SerializedName("requiere_vinculacion_bluetooth")
    val requiereVinculacionBluetooth: Boolean
)
