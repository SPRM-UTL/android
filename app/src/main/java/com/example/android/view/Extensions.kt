package com.example.android.view

import android.view.View
import androidx.core.content.ContextCompat
import com.example.android.R
import com.google.android.material.snackbar.Snackbar

object Snackbars {

    fun info(view: View, texto: String, duracion: Int = Snackbar.LENGTH_LONG): Snackbar {
        val snackbar = Snackbar.make(view, texto, duracion)

        snackbar.setBackgroundTint(ContextCompat.getColor(view.context, R.color.teal_primary))
        snackbar.setTextColor(ContextCompat.getColor(view.context, android.R.color.white))

        return snackbar
    }
    fun error(view: View, texto: String, duracion: Int = Snackbar.LENGTH_LONG): Snackbar {
        val snackbar = Snackbar.make(view, texto, duracion)

        snackbar.setBackgroundTint(ContextCompat.getColor(view.context, R.color.red))
        snackbar.setTextColor(ContextCompat.getColor(view.context, android.R.color.white))

        return snackbar
    }

    fun success(view: View, texto: String, duracion: Int = Snackbar.LENGTH_LONG): Snackbar {
        val snackbar = Snackbar.make(view, texto, duracion)

        snackbar.setBackgroundTint(ContextCompat.getColor(view.context, R.color.teal_medium))
        snackbar.setTextColor(ContextCompat.getColor(view.context, android.R.color.white))

        return snackbar
    }

    fun warning(view: View, texto: String, duracion: Int = Snackbar.LENGTH_LONG): Snackbar {
        val snackbar = Snackbar.make(view, texto, duracion)

        // Usamos un color naranja/amarillo para advertencias. 
        // Si no existe en colors.xml, Material lo manejará o podemos usar uno hardcoded suave
        snackbar.setBackgroundTint(android.graphics.Color.parseColor("#FFA000")) 
        snackbar.setTextColor(android.graphics.Color.WHITE)

        return snackbar
    }
}