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
        adjustSnackbarInsets(snackbar, view)
        return snackbar
    }

    fun error(view: View, texto: String, duracion: Int = Snackbar.LENGTH_LONG): Snackbar {
        val snackbar = Snackbar.make(view, texto, duracion)
        snackbar.setBackgroundTint(ContextCompat.getColor(view.context, R.color.red))
        snackbar.setTextColor(ContextCompat.getColor(view.context, android.R.color.white))
        adjustSnackbarInsets(snackbar, view)
        return snackbar
    }

    fun success(view: View, texto: String, duracion: Int = Snackbar.LENGTH_LONG): Snackbar {
        val snackbar = Snackbar.make(view, texto, duracion)
        snackbar.setBackgroundTint(ContextCompat.getColor(view.context, R.color.teal_medium))
        snackbar.setTextColor(ContextCompat.getColor(view.context, android.R.color.white))
        adjustSnackbarInsets(snackbar, view)
        return snackbar
    }

    fun warning(view: View, texto: String, duracion: Int = Snackbar.LENGTH_LONG): Snackbar {
        val snackbar = Snackbar.make(view, texto, duracion)
        snackbar.setBackgroundTint(android.graphics.Color.parseColor("#FFA000")) 
        snackbar.setTextColor(android.graphics.Color.WHITE)
        adjustSnackbarInsets(snackbar, view)
        return snackbar
    }

    private fun adjustSnackbarInsets(snackbar: Snackbar, view: View) {
        val bottomBar = view.rootView.findViewById<View>(R.id.bottom_bar_container)
        
        if (bottomBar != null && bottomBar.visibility == View.VISIBLE) {
            snackbar.anchorView = bottomBar
        } else {
            val snackbarView = snackbar.view
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(snackbarView) { v, insets ->
                val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                val params = v.layoutParams as? android.view.ViewGroup.MarginLayoutParams
                if (params != null) {
                    params.bottomMargin = systemBars.bottom + (16 * v.resources.displayMetrics.density).toInt()
                    v.layoutParams = params
                }
                insets
            }
        }
    }
}