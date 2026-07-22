package com.example.android.feature.ai.data.state
import com.example.android.feature.ai.data.state.CameraSharedState
import com.example.android.R

import android.graphics.Bitmap
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


object CameraSharedState {
    @Volatile
    var isServiceRunning: Boolean = false

    @Volatile
    var latestBitmap: Bitmap? = null

    @Volatile
    var lastHandResult: HandLandmarkerResult? = null

    @Volatile
    var currentAction: String = "Ninguno"

    @Volatile
    var currentGesture: String = "Ninguno"

    @Volatile
    var imageWidth: Int = 1

    @Volatile
    var imageHeight: Int = 1

    @Volatile
    var lastHandConfidence: Float = 0f

    // StateFlows para UI reactiva
    private val _uiBitmap = MutableStateFlow<Bitmap?>(null)
    val uiBitmap: StateFlow<Bitmap?> = _uiBitmap.asStateFlow()

    private val _uiGesture = MutableStateFlow("Ninguno")
    val uiGesture: StateFlow<String> = _uiGesture.asStateFlow()

    private val _uiServiceRunning = MutableStateFlow(false)
    val uiServiceRunning: StateFlow<Boolean> = _uiServiceRunning.asStateFlow()

    private val _uiConfidence = MutableStateFlow(0f)
    val uiConfidence: StateFlow<Float> = _uiConfidence.asStateFlow()

    fun updateUiState() {
        _uiBitmap.value = latestBitmap
        _uiGesture.value = currentGesture
        _uiServiceRunning.value = isServiceRunning
        _uiConfidence.value = lastHandConfidence
    }
}
