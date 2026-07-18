package com.example.android.feature.ai.domain.models
import com.example.android.feature.ai.domain.models.GestureAnalyzerConfig

object GestureAnalyzerConfig {
    private var activeGestures: Map<String, Boolean> = emptyMap()

    fun updateConfig(newConfig: Map<String, Boolean>) {
        activeGestures = newConfig
    }

    fun isGestureActive(gestureName: String): Boolean {
        // Si no hay configuración o no está en el mapa, asumimos que está activo por defecto
        return activeGestures[gestureName] ?: true
    }
}
