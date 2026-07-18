package com.example.android.feature.ai.domain.analyzer

import com.example.android.feature.ai.domain.models.FingerState
import com.example.android.feature.ai.domain.models.Vector3D

object FingerAnalyzer {

    // Nombres de los dedos
    const val THUMB = "THUMB"
    const val INDEX = "INDEX"
    const val MIDDLE = "MIDDLE"
    const val RING = "RING"
    const val PINKY = "PINKY"

    // Umbral de flexibilidad en grados (180 es completamente recto).
    // Permitimos que esté ligeramente doblado y aún se considere extendido.
    private const val EXTENSION_THRESHOLD = 145f 

    /**
     * Calcula el estado de cada uno de los 5 dedos de la mano usando geometría 3D.
     */
    fun analyzeFingers(localLandmarks: List<Vector3D>): List<FingerState> {
        if (localLandmarks.size < 21) return emptyList()

        val states = mutableListOf<FingerState>()

        // 1. Thumb (Pulgar)
        // Puntos: CMC(1), MCP(2), IP(3), TIP(4)
        val thumbAngle = calculateInternalAngle(localLandmarks[2], localLandmarks[3], localLandmarks[4])
        // El pulgar tiene una articulación menos pronunciada, ajustamos la detección.
        val thumbExtended = isThumbExtended(localLandmarks)
        states.add(FingerState(THUMB, thumbExtended, thumbAngle, localLandmarks[4] - localLandmarks[3]))

        // 2. Index (Índice)
        // Puntos: MCP(5), PIP(6), DIP(7), TIP(8)
        val indexAngle = calculateInternalAngle(localLandmarks[5], localLandmarks[6], localLandmarks[8])
        states.add(FingerState(INDEX, indexAngle > EXTENSION_THRESHOLD, indexAngle, localLandmarks[8] - localLandmarks[6]))

        // 3. Middle (Medio)
        val middleAngle = calculateInternalAngle(localLandmarks[9], localLandmarks[10], localLandmarks[12])
        states.add(FingerState(MIDDLE, middleAngle > EXTENSION_THRESHOLD, middleAngle, localLandmarks[12] - localLandmarks[10]))

        // 4. Ring (Anular)
        val ringAngle = calculateInternalAngle(localLandmarks[13], localLandmarks[14], localLandmarks[16])
        states.add(FingerState(RING, ringAngle > EXTENSION_THRESHOLD, ringAngle, localLandmarks[16] - localLandmarks[14]))

        // 5. Pinky (Meñique)
        val pinkyAngle = calculateInternalAngle(localLandmarks[17], localLandmarks[18], localLandmarks[20])
        states.add(FingerState(PINKY, pinkyAngle > EXTENSION_THRESHOLD, pinkyAngle, localLandmarks[20] - localLandmarks[18]))

        return states
    }

    /**
     * Calcula el ángulo interno (en grados) formado en el punto B por los segmentos A-B y C-B.
     * Si los 3 puntos están en una línea recta perfecta, devuelve 180°.
     */
    private fun calculateInternalAngle(a: Vector3D, b: Vector3D, c: Vector3D): Float {
        val vectorBA = a - b
        val vectorBC = c - b
        return vectorBA.angleBetween(vectorBC)
    }

    /**
     * Lógica especial para el pulgar, ya que su rango de movimiento y ángulo es distinto.
     * Evaluamos si la punta del pulgar está lo suficientemente alejada del nudillo base del índice.
     */
    private fun isThumbExtended(landmarks: List<Vector3D>): Boolean {
        // En lugar de usar la muñeca, medimos contra el MCP del meñique (17) y MCP del índice (5)
        // para asegurar que está extendido hacia afuera.
        val tip = landmarks[4]
        val ip = landmarks[3]
        val pinkyMcp = landmarks[17]
        
        // Distancia euclidiana 3D
        val distTipToPinky = (tip - pinkyMcp).magnitude()
        val distIpToPinky = (ip - pinkyMcp).magnitude()

        return distTipToPinky > distIpToPinky
    }
}
