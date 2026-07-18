package com.example.android.feature.ai.domain.analyzer

import com.example.android.feature.ai.domain.models.Vector3D
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

object HandNormalizer {
    
    /**
     * Convierte los NormalizedLandmarks de MediaPipe en vectores 3D locales relativos a la muñeca.
     * La muñeca siempre será el origen (0, 0, 0).
     */
    fun toLocalCoordinates(landmarks: List<NormalizedLandmark>): List<Vector3D> {
        if (landmarks.isEmpty()) return emptyList()

        val wrist = landmarks[0]
        
        return landmarks.map { landmark ->
            Vector3D(
                x = landmark.x() - wrist.x(),
                y = landmark.y() - wrist.y(),
                z = landmark.z() - wrist.z()
            )
        }
    }
}
