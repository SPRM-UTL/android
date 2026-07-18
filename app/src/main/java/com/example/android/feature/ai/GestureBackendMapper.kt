package com.example.android.feature.ai

/**
 * Traduce el nombre "libre" de un gesto detectado localmente (HandMetrics / activador / paso)
 * a uno de los nombres que el backend acepta (ver GestosController.GestosValidos):
 * "Manos Arriba", "Una Mano Arriba", "Agitar la Mano", "Abrir Puño", "Cerrar Puño".
 *
 * Esto es un mapeo por palabras clave, AJUSTAR si los nombres reales de HandMetrics no calzan bien.
 */
object GestureBackendMapper {

    fun mapToValidBackendName(rawGestureName: String): String {
        val upper = rawGestureName.uppercase()

        return when {
            upper.contains("PUNO") && (upper.contains("ABRIR") || upper.contains("PALMA")) -> "Abrir Puño"
            upper.contains("PUNO") -> "Cerrar Puño"
            upper.contains("PALMA") || upper.contains("ABIERT") -> "Abrir Puño"
            upper.contains("AGITAR") || upper.contains("WAVE") -> "Agitar la Mano"
            upper.contains("ARRIBA") && (upper.contains("DOS") || upper.contains("AMBAS")) -> "Manos Arriba"
            upper.contains("ARRIBA") -> "Una Mano Arriba"
            else -> "Agitar la Mano" // fallback genérico para nombres no reconocidos (L, ROCK, TE AMO ILY, etc.)
        }
    }
}
