package com.example.android
import com.example.android.core.voice.TtsManager

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class ManordomoApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Cargar el modo oscuro de forma global para toda la app antes de las Activities
        val sharedPref = getSharedPreferences("SesionApp", MODE_PRIVATE)
        val darkHabilitado = sharedPref.getBoolean("darkModeEnabled", false)

        if (darkHabilitado) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        com.example.android.core.voice.TtsManager.inicializar(this)
    }
}
