package com.example.android.feature.ai.domain.analyzer

import com.example.android.feature.ai.domain.models.PalmState
import com.example.android.feature.ai.domain.models.Vector3D
import kotlin.math.atan2

object PalmAnalyzer {

    /**
     * Calcula la orientación espacial de la palma (Normal y Rotación 2D en pantalla).
     */
    fun analyzePalm(localLandmarks: List<Vector3D>, handedness: String, isFrontCamera: Boolean = true): PalmState {
        if (localLandmarks.size < 21) {
            return PalmState(true, Vector3D(0f, 0f, 1f), 0f)
        }

        val wrist = localLandmarks[0]
        val indexMcp = localLandmarks[5]
        val pinkyMcp = localLandmarks[17]

        // Vector U: Muñeca -> Índice
        val vectorU = indexMcp - wrist
        // Vector V: Muñeca -> Meñique
        val vectorV = pinkyMcp - wrist

        // Normal de la palma = U x V (Producto Cruz 3D)
        val normal = vectorU.cross(vectorV).normalize()

        // Determinar si mira al frente (basado en la matemática antigua pero ahora con vector 3D real)
        // crossZ nos dice la dirección en el eje Z de la cámara
        val crossZ = normal.z

        val isFacingCamera = if (isFrontCamera) {
            if (handedness == "Left") crossZ > 0 else crossZ < 0
        } else {
            if (handedness == "Left") crossZ < 0 else crossZ > 0
        }

        // Calcular la rotación en Z (inclinación de la mano en el plano de la pantalla)
        // vector medio desde muñeca hasta MCP del dedo medio (9)
        val middleMcp = localLandmarks[9]
        val handDirection = middleMcp - wrist
        
        // Ángulo en el plano XY (-180 a 180)
        var rotationZ = Math.toDegrees(atan2(handDirection.y.toDouble(), handDirection.x.toDouble())).toFloat()
        
        // Ajustar rotación basada en cámara frontal (espejo)
        if (isFrontCamera) {
            rotationZ = -rotationZ
        }

        return PalmState(
            isFacingCamera = isFacingCamera,
            normal = normal,
            rotationZ = rotationZ
        )
    }
}
