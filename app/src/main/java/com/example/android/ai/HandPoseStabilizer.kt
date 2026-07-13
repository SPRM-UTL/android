package com.example.android.ai

/**
 * Estabiliza la pose para secuencias/combos.
 * La UI puede usar la detección cruda para respuesta inmediata.
 */
class HandPoseStabilizer(
    private val framesToConfirm: Int = 2,
    private val emptyFramesToClear: Int = 2
) {
    private var stablePose = ""
    private var candidatePose = ""
    private var candidateCount = 0
    private var emptyCount = 0

    fun update(rawPose: String): String {
        if (rawPose.isBlank()) {
            emptyCount++
            if (emptyCount >= emptyFramesToClear) {
                reset()
            }
            return stablePose
        }

        emptyCount = 0

        if (rawPose == stablePose) {
            candidatePose = rawPose
            candidateCount = framesToConfirm
            return stablePose
        }

        if (rawPose == candidatePose) {
            candidateCount++
        } else {
            candidatePose = rawPose
            candidateCount = 1
        }

        if (candidateCount >= framesToConfirm) {
            stablePose = rawPose
        }
        return stablePose
    }

    fun reset() {
        stablePose = ""
        candidatePose = ""
        candidateCount = 0
        emptyCount = 0
    }
}
