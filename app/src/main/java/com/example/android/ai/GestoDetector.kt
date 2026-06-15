package com.example.android.ai

import com.example.android.db.Gesto

class GestoTracker(val gesto: Gesto, private val onGestoDetected: (Gesto) -> Unit) {
    var framesHeld = 0
        private set
    var isTriggered = false
        private set

    fun reset() {
        framesHeld = 0
        isTriggered = false
    }

    fun processPoses(leftPose: String, rightPose: String) {
        val targetName = gesto.nombre ?: return
        
        // Asumimos un umbral fijo de 10 cuadros (aprox 300ms) para confirmar el gesto
        val framesRequired = 10 
        
        val leftMatch = leftPose.contains(targetName, ignoreCase = true)
        val rightMatch = rightPose.contains(targetName, ignoreCase = true)

        if (leftMatch || rightMatch) {
            if (!isTriggered) {
                framesHeld++
                if (framesHeld >= framesRequired) {
                    isTriggered = true
                    onGestoDetected(gesto)
                }
            }
        } else {
            framesHeld = 0
            isTriggered = false
        }
    }
}

class GestoDetector(private val onGestoDetected: (Gesto) -> Unit) {
    private val trackers = mutableListOf<GestoTracker>()
    private var lastTriggerTime: Long = 0

    fun updateGestos(newGestos: List<Gesto>) {
        trackers.clear()
        for (gesto in newGestos) {
            trackers.add(GestoTracker(gesto) { detectedGesto ->
                val currentTime = System.currentTimeMillis()
                // Evitar triggers múltiples en menos de 2 segundos (Cooldown)
                if (currentTime - lastTriggerTime > 2000) {
                    lastTriggerTime = currentTime
                    onGestoDetected(detectedGesto)
                }
            })
        }
    }

    fun processPoses(leftPose: String, rightPose: String) {
        trackers.forEach { it.processPoses(leftPose, rightPose) }
    }
}
