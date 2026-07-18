package com.example.android.feature.ai.domain.analyzer

import com.example.android.feature.ai.domain.models.FingerState
import com.example.android.feature.ai.domain.models.HandPose
import com.example.android.feature.ai.domain.models.PalmState

object GestureClassifier {

    /**
     * Clasifica un gesto basado únicamente en estados binarios y orientación espacial,
     * aislando totalmente la geometría.
     */
    fun classify(fingers: List<FingerState>, palm: PalmState): HandPose {
        if (fingers.size < 5) return HandPose.DESCONOCIDO

        val thumb = fingers[0]
        val index = fingers[1]
        val middle = fingers[2]
        val ring = fingers[3]
        val pinky = fingers[4]

        val booleanFingers = listOf(thumb.isExtended, index.isExtended, middle.isExtended, ring.isExtended, pinky.isExtended)

        // Lógica F_OK (pulgar e índice formando círculo, otros extendidos)
        // La distancia geométrica de las puntas ahora se aproxima evaluando que NO estén extendidos pero
        // el resto sí (esto podría requerir un ajuste de proximidad, pero en booleanos se ve así):
        if (!thumb.isExtended && !index.isExtended && middle.isExtended && ring.isExtended && pinky.isExtended) {
            // Nota: En la versión 3D completa el FingerAnalyzer debería indicar si "se tocan".
            // Para simplicidad en este clasificador puro, asumimos esto como F_OK.
            return HandPose.F_OK
        }

        // Pulgar Arriba / Abajo
        if (booleanFingers == listOf(true, false, false, false, false)) {
            // Si la rotación apunta hacia arriba (cerca de 0) -> Arriba
            // Si apunta hacia abajo (cerca de 180 o -180) -> Abajo
            // Asumimos que rotación en Z cerca de -90 es mano apuntando arriba en la pantalla.
            // Más fácil: verificar la dirección del vector del pulgar. 
            // Para simplificar, usamos la rotación de la palma.
            return if (palm.rotationZ > -45 && palm.rotationZ < 135) {
                // Ajuste heurístico simple basado en rotación 
                HandPose.A_PULGAR_ARRIBA
            } else {
                HandPose.A_PULGAR_ABAJO
            }
        }

        // U vs V_PAZ
        if (booleanFingers == listOf(false, true, true, false, false)) {
            // La diferencia geométrica requiere saber si los dedos están juntos o separados.
            // Para mantener el clasificador libre de math, lo aproximamos por el ángulo de los vectores de dirección.
            val angleBetween = index.direction.angleBetween(middle.direction)
            return if (angleBetween < 10f) {
                HandPose.U
            } else {
                HandPose.V_PAZ
            }
        }

        return when (booleanFingers) {
            listOf(false, false, false, false, false) -> HandPose.PUNO
            listOf(false, true, false, false, false) -> HandPose.D_UNO
            listOf(false, true, true, true, false) -> HandPose.W_TRES
            listOf(false, true, true, true, true) -> HandPose.B_CUATRO
            listOf(true, true, true, true, true) -> HandPose.CINCO_MANO_ABIERTA
            listOf(false, true, false, false, true) -> HandPose.ROCK
            listOf(true, true, false, false, true) -> HandPose.TE_AMO_ILY
            listOf(true, true, false, false, false) -> HandPose.L
            listOf(false, false, false, false, true) -> HandPose.I
            listOf(true, false, false, false, true) -> HandPose.Y
            else -> HandPose.DESCONOCIDO
        }
    }
}
