package com.example.android.voice

import android.content.Context
import com.example.android.db.AppDatabase
import com.example.android.db.Gesto
import java.text.Normalizer

object VoiceCommandMatcher {

    suspend fun encontrarGesto(context: Context, textoReconocido: String): Gesto? {
        val texto = normalizar(textoReconocido)
        val gestos = AppDatabase.getDatabase(context).gestoDao().getGestosConFraseVoz()

        return gestos.firstOrNull { normalizar(it.fraseVozActivadora ?: "") == texto }
            ?: gestos.firstOrNull {
                val frase = normalizar(it.fraseVozActivadora ?: "")
                frase.isNotBlank() && texto.contains(frase)
            }
    }

    private fun normalizar(s: String): String {
        val sinAcentos = Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}"), "")
        return sinAcentos.lowercase().trim()
    }
}
