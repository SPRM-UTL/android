package com.example.android.feature.ai

/**
 * Comparación estructurada de gestos de mano.
 * Evita falsos positivos por substring (ej. "L" dentro de "TE AMO ILY").
 */
object HandGestureMatcher {

    data class ParsedHandPose(
        val gestureName: String,
        val view: String? = null
    )

    private val posePattern = Regex("""^(.+?)\s*\[(Frente|Espalda)\]$""", RegexOption.IGNORE_CASE)

    fun format(gestureName: String, view: String): String = "$gestureName [$view]"

    fun parse(detected: String): ParsedHandPose? {
        val trimmed = detected.trim()
        if (trimmed.isEmpty()) return null

        val match = posePattern.matchEntire(trimmed)
        return if (match != null) {
            ParsedHandPose(
                gestureName = match.groupValues[1].trim(),
                view = match.groupValues[2]
            )
        } else {
            ParsedHandPose(gestureName = trimmed)
        }
    }

    fun matches(detected: String, expected: String): Boolean {
        val parsed = parse(detected) ?: return false
        return parsed.gestureName.equals(expected.trim(), ignoreCase = true)
    }
}
