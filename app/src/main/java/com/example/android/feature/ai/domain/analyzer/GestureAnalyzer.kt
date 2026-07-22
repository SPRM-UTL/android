package com.example.android.feature.ai.domain.analyzer
import com.example.android.feature.ai.domain.analyzer.SecuenciaDetector
import com.example.android.feature.ai.data.state.CameraSharedState
import com.example.android.feature.ai.domain.models.GestureAnalyzerConfig
import com.example.android.feature.ai.domain.manager.Combo
import com.example.android.feature.ai.domain.analyzer.HandNormalizer
import com.example.android.feature.ai.domain.analyzer.FingerAnalyzer
import com.example.android.feature.ai.domain.analyzer.PalmAnalyzer
import com.example.android.feature.ai.domain.analyzer.GestureClassifier
import com.example.android.feature.ai.domain.analyzer.GestureAnalyzer
import com.example.android.feature.ai.domain.utils.HandLandmarkSmoother
import com.example.android.feature.ai.domain.utils.HandPoseStabilizer
import com.example.android.R

import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class GestureAnalyzer {

    var onComboCompleted: ((Combo) -> Unit)? = null
    val sequenceDetector = SecuenciaDetector { combo -> onComboCompleted?.invoke(combo) }

    // Smoother EMA de landmarks crudos (reduce jitter antes de clasificar)
    private val landmarkSmoother = HandLandmarkSmoother()

    // Stabilizers de pose: 2 frames para confirmar, 3 frames vacíos para limpiar
    private val leftStabilizer = HandPoseStabilizer(framesToConfirm = 2, emptyFramesToClear = 3)
    private val rightStabilizer = HandPoseStabilizer(framesToConfirm = 2, emptyFramesToClear = 3)

    // Frame skipping: analizar cada 2do frame para reducir carga de CPU/GPU
    private var frameCounter = 0
    private val FRAME_SKIP_INTERVAL = 2

    // Confidence: umbral mínimo de handedness score para aceptar una detección
    var minHandednessScore: Float = 0.6f

    // Confidence: último score máximo detectado (para UI)
    var lastMaxConfidence: Float = 0f
        private set

    fun analyze(
        handResult: HandLandmarkerResult?,
        isFrontCamera: Boolean = true
    ): String {
        var action = "Ninguno"

        // Frame skipping: incrementar contador y saltar frames impares
        frameCounter++
        if (frameCounter % FRAME_SKIP_INTERVAL != 0) {
            return CameraSharedState.currentAction ?: action
        }

        val handLandmarks = handResult?.landmarks()
        val handednesses = handResult?.handednesses()

        // 4. Manos (Gestos Estáticos con Suavizado Temporal)
        var leftPose = ""
        var rightPose = ""
        var handDetected = false
        lastMaxConfidence = 0f

        if (handLandmarks != null && handednesses != null && handLandmarks.isNotEmpty()) {
            for (i in handLandmarks.indices) {
                val hand = handLandmarks[i]
                val handednessCategory = handednesses[i].first()
                val handedness = handednessCategory.categoryName() // "Left" or "Right"
                val handScore = handednessCategory.score() // Float 0.0..1.0

                // Confidence: filtrar manos con score bajo
                if (handScore < minHandednessScore) continue

                // Trackear confidence máxima para UI
                if (handScore > lastMaxConfidence) {
                    lastMaxConfidence = handScore
                }

                handDetected = true

                // Determinar mano real ANTES de suavizar para no mezclar historiales
                val realHandednessForSmoothing = if (isFrontCamera) {
                    if (handedness == "Left") "Right" else "Left"
                } else {
                    handedness
                }

                // Suavizar landmarks crudos con EMA antes de normalizar
                val smoothedHand = landmarkSmoother.smooth(hand, realHandednessForSmoothing)

                // Nueva tubería 3D (sobre landmarks suavizados)
                val localLandmarks = HandNormalizer.toLocalCoordinates(smoothedHand)
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

        // Suavizado temporal: stabilizer de confirmación rápida (2 frames) en lugar de voto de mayoría de 7
        val smoothedLeftPose = leftStabilizer.update(leftPose)
        val smoothedRightPose = rightStabilizer.update(rightPose)

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
        CameraSharedState.lastHandConfidence = lastMaxConfidence

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
