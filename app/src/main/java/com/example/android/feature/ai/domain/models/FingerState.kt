package com.example.android.feature.ai.domain.models

data class FingerState(
    val fingerName: String,
    val isExtended: Boolean,
    val angle: Float,
    val direction: Vector3D // Dirección hacia donde apunta (ej. MCP -> TIP normalizado)
)
