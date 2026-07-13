package com.example.android.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

object TtsManager {
    private var tts: TextToSpeech? = null
    private var listo = false
    var confirmacionHabladaActivada: Boolean = true

    fun inicializar(context: Context) {
        tts = TextToSpeech(context.applicationContext) { status ->
            listo = status == TextToSpeech.SUCCESS
            tts?.language = Locale("es", "MX")
        }
    }

    fun anunciar(texto: String) {
        if (!listo || !confirmacionHabladaActivada) return
        tts?.speak(texto, TextToSpeech.QUEUE_ADD, null, null)
    }

    fun aplicarVoz(voiceName: String) {
        val voz = tts?.voices?.firstOrNull { it.name == voiceName }
        if (voz != null) tts?.voice = voz
    }

    fun aplicarVelocidad(rate: Float) {
        tts?.setSpeechRate(rate)
    }

    fun getVocesDisponibles(): List<Voice> =
        tts?.voices?.filter { it.locale.language == "es" }?.toList() ?: emptyList()

    fun liberar() {
        tts?.stop()
        tts?.shutdown()
    }
}
