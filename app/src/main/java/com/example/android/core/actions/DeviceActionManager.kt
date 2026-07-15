package com.example.android.core.actions

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.example.android.core.db.Dispositivo
import com.example.android.core.network.BluetoothController
import com.example.android.core.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DeviceActionManager {

    const val ACTION_VOLUME = "ACTION_VOLUME"
    const val ACTION_POWER = "ACTION_POWER"

    /**
     * Ejecuta una acción en el dispositivo.
     * @param context Contexto de Android (necesario para AudioManager).
     * @param dispositivo Instancia del dispositivo sobre el que se actúa.
     * @param tipoAccion El tipo de acción (e.g. ACTION_VOLUME, ACTION_POWER).
     * @param valor Valor opcional de la acción (e.g. porcentaje de volumen 0-100, o un boolean para power).
     */
    fun ejecutarAccion(context: Context, dispositivo: Dispositivo, tipoAccion: String, valor: Any?) {
        val tipo = (dispositivo.tipo ?: "").lowercase()

        when (tipoAccion) {
            ACTION_VOLUME -> {
                // Si es un dispositivo de audio (audífonos, bocina), manejamos el volumen nativo del sistema
                if (esDispositivoDeAudio(tipo)) {
                    val porcentaje = (valor as? Float) ?: 0f
                    ajustarVolumenSistema(context, porcentaje)
                } else {
                    // Aquí podríamos enviar comandos de volumen por serial si el hardware lo soporta (e.g. ESP32 con dimmer)
                    Log.d("DeviceActionManager", "Acción de volumen no implementada para el tipo: $tipo")
                }
            }
            ACTION_POWER -> {
                val encendido = (valor as? Boolean) ?: false
                
                val comandoBase = if (encendido) "ON" else "OFF"
                
                // Si el dispositivo es Bluetooth serial (como ESP32)
                if (BluetoothController.isConnected) {
                    val comandoFinal = "$comandoBase\n"
                    BluetoothController.enviarComando(comandoFinal)
                    Log.d("DeviceActionManager", "Comando serial enviado: $comandoFinal")
                } else {
                    Log.d("DeviceActionManager", "Ejecutando acción por WiFi HTTP para $tipo")
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            // Usar la dirección MAC del dispositivo como deviceKey
                            val deviceKey = dispositivo.macBluetooth ?: ""
                            val response = RetrofitClient.deviceService.enviarComandoWebSocket(comandoBase, deviceKey)
                            if (response.isSuccessful) {
                                Log.d("DeviceActionManager", "Comando WiFi enviado con éxito: $comandoBase")
                            } else {
                                Log.e("DeviceActionManager", "Error enviando comando WiFi: ${response.code()}")
                            }
                        } catch (e: Exception) {
                            Log.e("DeviceActionManager", "Excepción enviando comando WiFi", e)
                        }
                    }
                }
            }
            else -> {
                Log.w("DeviceActionManager", "Acción desconocida: $tipoAccion")
            }
        }
    }

    private fun esDispositivoDeAudio(tipo: String): Boolean {
        return tipo.contains("audífono") || tipo.contains("audifono") || 
               tipo.contains("bocina") || tipo.contains("audio") || 
               tipo.contains("speaker") || tipo.contains("auricular")
    }

    private fun ajustarVolumenSistema(context: Context, porcentaje: Float) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        
        // Calcular el volumen objetivo basado en el porcentaje (0.0 a 100.0)
        val targetVolume = ((porcentaje / 100f) * maxVolume).toInt()
        
        // Ajustar el volumen (FLAG_SHOW_UI muestra la barra de volumen del sistema brevemente)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, AudioManager.FLAG_SHOW_UI)
        Log.d("DeviceActionManager", "Volumen ajustado a $targetVolume de $maxVolume")
    }
}
