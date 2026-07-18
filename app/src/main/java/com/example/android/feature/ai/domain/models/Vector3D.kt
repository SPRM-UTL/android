package com.example.android.feature.ai.domain.models

import kotlin.math.acos
import kotlin.math.sqrt

data class Vector3D(val x: Float, val y: Float, val z: Float) {
    
    operator fun minus(other: Vector3D): Vector3D {
        return Vector3D(x - other.x, y - other.y, z - other.z)
    }

    operator fun plus(other: Vector3D): Vector3D {
        return Vector3D(x + other.x, y + other.y, z + other.z)
    }

    fun magnitude(): Float {
        return sqrt(x * x + y * y + z * z)
    }

    fun normalize(): Vector3D {
        val mag = magnitude()
        if (mag == 0f) return this
        return Vector3D(x / mag, y / mag, z / mag)
    }

    fun dot(other: Vector3D): Float {
        return x * other.x + y * other.y + z * other.z
    }

    fun cross(other: Vector3D): Vector3D {
        return Vector3D(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x
        )
    }

    /**
     * Devuelve el ángulo en grados entre este vector y otro vector.
     */
    fun angleBetween(other: Vector3D): Float {
        val mag1 = this.magnitude()
        val mag2 = other.magnitude()
        if (mag1 == 0f || mag2 == 0f) return 0f
        
        var cosTheta = this.dot(other) / (mag1 * mag2)
        // Clamp para evitar errores de precisión de punto flotante
        cosTheta = cosTheta.coerceIn(-1f, 1f)
        
        return Math.toDegrees(acos(cosTheta.toDouble())).toFloat()
    }
}
