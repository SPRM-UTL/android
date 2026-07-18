package com.example.android.feature.ai.domain.analyzer
import com.example.android.feature.ai.domain.analyzer.SecuenciaDetector
import com.example.android.feature.ai.data.state.CameraSharedState
import com.example.android.feature.ai.domain.models.GestureAnalyzerConfig
import com.example.android.feature.ai.domain.manager.Combo
import com.example.android.feature.ai.domain.models.HandMetrics
import com.example.android.feature.ai.domain.analyzer.HandNormalizer
import com.example.android.feature.ai.domain.analyzer.FingerAnalyzer
import com.example.android.feature.ai.domain.analyzer.PalmAnalyzer
import com.example.android.feature.ai.domain.analyzer.GestureClassifier
import com.example.android.feature.ai.domain.analyzer.GestureAnalyzer
import com.example.android.R

import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class GestureAnalyzer {

    var onComboCompleted: ((Combo) -> Unit)? = null
    val sequenceDetector = SecuenciaDetector { combo -> onComboCompleted?.invoke(combo) }

    // History for hand temporal smoothing
    private val leftHandPoseHistory = mutableListOf<String>()
    private val rightHandPoseHistory = mutableListOf<String>()
    private val handHistorySize = 7 // Smoothing window

    @Synchronized
    fun analyze(
        handResult: HandLandmarkerResult?,
        isFrontCamera: Boolean = true
    ): String {
        var action = "Ninguno"


        val handLandmarks = handResult?.landmarks()
        val handednesses = handResult?.handednesses()

        // 4. Manos (Gestos Estáticos con Suavizado Temporal)
        var leftPose = ""
        var rightPose = ""
        var handDetected = false

        if (handLandmarks != null && handednesses != null && handLandmarks.isNotEmpty()) {
            for (i in handLandmarks.indices) {
                val hand = handLandmarks[i]
                val handedness = handednesses[i].first().categoryName() // "Left" or "Right"

                handDetected = true
                // Nueva tubería 3D
                val localLandmarks = HandNormalizer.toLocalCoordinates(hand)
                val fingers = FingerAnalyzer.analyzeFingers(localLandmarks)
                val palm = PalmAnalyzer.analyzePalm(localLandmarks, handedness, isFrontCamera)
                
                val pose = GestureClassifier.classify(fingers, palm)
                val isFrente = palm.isFacingCamera
                val vistaText = if (isFrente) "Frente" else "Espalda"

                val baseName = pose.name.replace("_", " ")
                
                // Aplicar el filtro de la configuración del usuario
                if (GestureAnalyzerConfig.isGestureActive(baseName)) {
                    val nombreGesto = "$baseName [$vistaText]"

                    // Si la cámara es frontal (espejo), la "Izquierda" de la imagen es la "Derecha" real del usuario.
                    val realHandedness = if (isFrontCamera) {
                        if (handedness == "Left") "Right" else "Left"
                    } else {
                        handedness
                    }

                    if (realHandedness == "Right") {
                        rightPose = nombreGesto
                    } else if (realHandedness == "Left") {
                        leftPose = nombreGesto
                    }
                }
            }
        }

        // Apply Temporal Smoothing
        if (leftPose.isNotEmpty()) {
            leftHandPoseHistory.add(leftPose)
        } else {
            leftHandPoseHistory.add("") // Vacío si no se detectó mano izquierda
        }

        if (rightPose.isNotEmpty()) {
            rightHandPoseHistory.add(rightPose)
        } else {
            rightHandPoseHistory.add("") // Vacío si no se detectó mano derecha
        }

        if (leftHandPoseHistory.size > handHistorySize) leftHandPoseHistory.removeAt(0)
        if (rightHandPoseHistory.size > handHistorySize) rightHandPoseHistory.removeAt(0)

        // Obtener la pose más frecuente (Moda) en la ventana de tiempo
        val smoothedLeftPose = leftHandPoseHistory.filter { it.isNotEmpty() }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: ""
        val smoothedRightPose = rightHandPoseHistory.filter { it.isNotEmpty() }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: ""

        // Process sequence
        sequenceDetector.processPoses(smoothedLeftPose, smoothedRightPose)

        // Nombre "limpio" del gesto detectado en este instante, SIN el feedback de los combos.
        // Este es el valor que se debe usar para GUARDAR una acción/combo nuevo.
        val gestureName: String = if (smoothedLeftPose.isNotEmpty() || smoothedRightPose.isNotEmpty()) {
            val leftStr = if (smoothedLeftPose.isNotEmpty()) "Izq: $smoothedLeftPose" else ""
            val rightStr = if (smoothedRightPose.isNotEmpty()) "Der: $smoothedRightPose" else ""
            val separator = if (leftStr.isNotEmpty() && rightStr.isNotEmpty()) ", " else ""
            "$leftStr$separator$rightStr"
        } else {
            action // "Ninguno"
        }

        CameraSharedState.currentGesture = gestureName

        // Texto completo para mostrar en el overlay de la cámara
        // (este sí incluye el feedback de todos los combos configurados)
        action = if (gestureName != "Ninguno") {
            "$gestureName\n\n${sequenceDetector.feedbackText}"
        } else {
            sequenceDetector.feedbackText
        }

        return action
    }
}
