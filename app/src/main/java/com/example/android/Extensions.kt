package com.example.android

import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar

object Snackbars {

    fun info(view: View, texto: String, duracion: Int = Snackbar.LENGTH_LONG): Snackbar {
        val snackbar = Snackbar.make(view, texto, duracion)

        snackbar.setBackgroundTint(ContextCompat.getColor(view.context, R.color.teal_primary))
        snackbar.setTextColor(ContextCompat.getColor(view.context, android.R.color.white))

        return snackbar
    }
}