package com.example.android.feature.ai.domain.models

data class PalmState(
    val isFacingCamera: Boolean,
    val normal: Vector3D,
    val rotationZ: Float // Ángulo de rotación de la mano (e.g. apuntando hacia arriba, de lado, hacia abajo)
)
