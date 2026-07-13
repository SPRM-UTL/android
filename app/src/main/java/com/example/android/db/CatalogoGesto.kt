package com.example.android.db

import com.google.gson.annotations.SerializedName

data class CatalogoGesto(
    @SerializedName("skCatalogoGestoId") val id: Int,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("icono") val icono: String,
    @SerializedName("isBodyGesture") val isBodyGesture: Boolean,
    @SerializedName("isActive") var isActive: Boolean = true
)

data class GuardarConfiguracionGestosDto(
    @SerializedName("skCatalogoGestoId") val skCatalogoGestoId: Int,
    @SerializedName("isActive") val isActive: Boolean
)
