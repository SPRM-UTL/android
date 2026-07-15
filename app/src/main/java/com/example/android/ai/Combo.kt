package com.example.android.ai

// ✅ DEJA SOLO ESTA DEFINICIÓN AQUÍ
data class Combo(
    val id: String,
    var name: String,
    var icono: String = "lucide_star",
    var activatorGesture: String? = null,
    var fraseVozActivadora: String? = null,
    var stepsCount: Int = 0,
    var aparatoId: Int? = null,
    var backendGestoId: Int? = null,
    var accionEncendido: Boolean? = null,
    var accionVinculada: String? = null,
    var pasos: MutableList<PasoSecuencia> = mutableListOf(),
    var activador: PasoSecuencia? = null
)