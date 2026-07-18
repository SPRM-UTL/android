package com.example.android.feature.ai.domain.utils
import com.example.android.feature.ai.domain.utils.HandLandmarkSmoother

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * Suaviza coordenadas de landmarks por mano usando media móvil exponencial (EMA).
 * Reduce jitter antes de clasificar el gesto.
 */
class HandLandmarkSmoother(
    private val alpha: Float = 0.65f
) {
    private val leftState = FloatArray(LANDMARK_COUNT * 3)
    private val rightState = FloatArray(LANDMARK_COUNT * 3)
    private var leftInitialized = false
    private var rightInitialized = false

    fun smooth(
        landmarks: List<NormalizedLandmark>,
        realHandedness: String
    ): List<NormalizedLandmark> {
        val isRight = realHandedness == "Right"
        val state = if (isRight) rightState else leftState
        var initialized = if (isRight) rightInitialized else leftInitialized

        val smoothed = ArrayList<NormalizedLandmark>(landmarks.size)
        for (i in landmarks.indices) {
            val raw = landmarks[i]
            val base = i * 3
            if (!initialized) {
                state[base] = raw.x()
                state[base + 1] = raw.y()
                state[base + 2] = raw.z()
            } else {
                state[base] = ema(state[base], raw.x())
                state[base + 1] = ema(state[base + 1], raw.y())
                state[base + 2] = ema(state[base + 2], raw.z())
            }
            smoothed.add(
                NormalizedLandmark.create(
                    state[base],
                    state[base + 1],
                    state[base + 2]
                )
            )
        }

        if (!initialized) {
            if (isRight) rightInitialized = true else leftInitialized = true
        }
        return smoothed
    }

    fun reset(realHandedness: String? = null) {
        when (realHandedness) {
            "Left" -> {
                leftState.fill(0f)
                leftInitialized = false
            }
            "Right" -> {
                rightState.fill(0f)
                rightInitialized = false
            }
            else -> {
                leftState.fill(0f)
                rightState.fill(0f)
                leftInitialized = false
                rightInitialized = false
            }
        }
    }

    private fun ema(previous: Float, current: Float): Float {
        return previous + alpha * (current - previous)
    }

    companion object {
        private const val LANDMARK_COUNT = 21
    }
}
