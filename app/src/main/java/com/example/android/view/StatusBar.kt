package com.example.android.view

import android.app.Activity
import android.os.Build
import android.view.WindowInsetsController
import androidx.core.content.ContextCompat // Asegúrate de importar esto

fun Activity.cambiarColorStatusBar(colorRes: Int, iconosClaros: Boolean = true) {

    window.statusBarColor = ContextCompat.getColor(this, colorRes)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val controller = window.insetsController
        if (controller != null) {
            if (iconosClaros) {
                controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
            } else {
                controller.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            }
        }
    } else {
        @Suppress("DEPRECATION")
        if (iconosClaros) {
            window.decorView.systemUiVisibility = 0
        } else {
            window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }
}