package com.example.android.core.actions
import com.example.android.feature.ai.domain.manager.Combo
import com.example.android.core.db.models.Gesto

import android.content.Context
import android.util.Log
import com.example.android.core.db.init.AppDatabase
import com.example.android.core.network.client.RetrofitClient
import com.example.android.core.voice.TtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GestureActionExecutor {

    private const val TAG = "GestureActionExecutor"

    suspend fun executeComboAction(context: Context, combo: Combo): Boolean {
        val aparatoId = combo.aparatoId
        if (aparatoId == null || aparatoId <= 0) {
            Log.w(TAG, "Combo '${combo.name}' completado sin dispositivo vinculado.")
            return false
        }

        val encendido = resolveTargetState(aparatoId, combo.accionEncendido, combo.contactoOutlet)
        return withContext(Dispatchers.IO) {
            try {
                val response = if (combo.contactoOutlet != null && combo.contactoOutlet!! >= 1 && combo.contactoOutlet!! <= 4) {
                    Log.i(TAG, "Combo '${combo.name}' → multisocket outlet ${combo.contactoOutlet}")
                    RetrofitClient.deviceService.toggleAparatoContacto(aparatoId, combo.contactoOutlet!!, encendido)
                } else {
                    RetrofitClient.deviceService.toggleAparato(aparatoId, encendido, combo.backendGestoId)
                }

                if (!response.isSuccessful) {
                    Log.w(TAG, "Toggle falló (${response.code()}) para aparato $aparatoId")
                    return@withContext false
                }

                val estadoConfirmado = response.body()?.estadoEncendido ?: encendido
                actualizarEstadoLocal(context, aparatoId, estadoConfirmado)
                
                val nombreDispositivo = obtenerNombreDispositivo(context, aparatoId)
                val outlet = if (combo.contactoOutlet != null) " contacto ${combo.contactoOutlet}" else ""
                val mensaje = if (estadoConfirmado) "Encendí $nombreDispositivo$outlet" else "Apagué $nombreDispositivo$outlet"
                TtsManager.anunciar(mensaje)

                Log.i(TAG, "Combo '${combo.name}' → aparato $aparatoId ${if (estadoConfirmado) "ON" else "OFF"}$outlet")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error ejecutando acción del combo '${combo.name}'", e)
                false
            }
        }
    }

    private suspend fun resolveTargetState(aparatoId: Int, accionEncendido: Boolean?, contactoOutlet: Int? = null): Boolean {
        if (accionEncendido != null) return accionEncendido

        return try {
            val response = RetrofitClient.deviceService.getAparatoEstado(aparatoId)
            if (response.isSuccessful) {
                val body = response.body()
                if (contactoOutlet != null && body != null) {
                    when (contactoOutlet) {
                        1 -> !(body.estadoEncendido ?: false)
                        2 -> !(body.estadoEncendido2 ?: false)
                        3 -> !(body.estadoEncendido3 ?: false)
                        4 -> !(body.estadoEncendido4 ?: false)
                        else -> !(body.estadoEncendido ?: false)
                    }
                } else {
                    !(body?.estadoEncendido ?: false)
                }
            } else {
                true
            }
        } catch (_: Exception) {
            true
        }
    }

    private suspend fun actualizarEstadoLocal(context: Context, aparatoId: Int, encendido: Boolean) {
        try {
            val db = AppDatabase.getDatabase(context)
            val dispositivo = db.dispositivoDao().getDispositivoById(aparatoId) ?: return
            db.dispositivoDao().insertDispositivo(dispositivo.copy(estadoEncendido = encendido))
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo actualizar estado local del dispositivo $aparatoId", e)
        }
    }

    private suspend fun obtenerNombreDispositivo(context: Context, aparatoId: Int): String {
        return try {
            val db = AppDatabase.getDatabase(context)
            db.dispositivoDao().getDispositivoById(aparatoId)?.nombre ?: "el dispositivo"
        } catch (e: Exception) {
            "el dispositivo"
        }
    }

    suspend fun executeVoiceAction(context: Context, gesto: com.example.android.core.db.models.Gesto): Boolean {
        val aparatoId = gesto.aparatoId
        if (aparatoId == null || aparatoId <= 0) {
            Log.w(TAG, "Gesto de voz '${gesto.nombre}' sin dispositivo vinculado.")
            return false
        }

        val encendido = resolveTargetState(aparatoId, null)
        return withContext(Dispatchers.IO) {
            try {
                val response = RetrofitClient.deviceService.toggleAparato(aparatoId, encendido, gesto.id)
                if (!response.isSuccessful) {
                    Log.w(TAG, "Toggle por voz falló (${response.code()}) para aparato $aparatoId")
                    return@withContext false
                }
                val estadoConfirmado = response.body()?.estadoEncendido ?: encendido
                actualizarEstadoLocal(context, aparatoId, estadoConfirmado)

                val nombreDispositivo = obtenerNombreDispositivo(context, aparatoId)
                val mensaje = if (estadoConfirmado) "Encendí $nombreDispositivo" else "Apagué $nombreDispositivo"
                TtsManager.anunciar(mensaje)

                Log.i(TAG, "Gesto de voz '${gesto.nombre}' → aparato $aparatoId ${if (estadoConfirmado) "ON" else "OFF"}")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error ejecutando acción por voz '${gesto.nombre}'", e)
                false
            }
        }
    }
}
