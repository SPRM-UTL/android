// Guardar como: adapters/GestureDropdownAdapter.kt
package com.example.android.adapters // Ajusta el package según tu estructura

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.android.R
import java.util.Locale

/**
 * Adapter personalizado para mostrar gestos/manos.
 * Forza las mayúsculas, el color azul oscuro (#073F4C) y estilo Bold
 * para que coincida perfectamente con el primer menú.
 */
class GestureDropdownAdapter(
    context: Context,
    items: List<String>,
    private val iconProvider: (String) -> Int? = { null } // Proveedor de iconos opcional
) : ArrayAdapter<String>(context, 0, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createItemView(position, convertView, parent)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createItemView(position, convertView, parent)
    }

    private fun createItemView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.dropdown_item, parent, false)

        val item = getItem(position) ?: return view

        // Obtener referencias
        val tvItemText = view.findViewById<TextView>(R.id.tvItemText)
        val ivItemIcon = view.findViewById<ImageView>(R.id.ivItemIcon)

        // Configurar texto: MAYÚSCULAS, Color Azul Oscuro (#073F4C) y Bold
        tvItemText.text = item.uppercase(Locale.getDefault())
        tvItemText.setTextColor(Color.parseColor("#073F4C"))
        tvItemText.setTypeface(null, Typeface.BOLD)

        // Determinar qué icono usar (el provisto o el genérico por defecto de Manordomo)
        val iconRes = iconProvider(item) ?: R.drawable.ic_manordomo_sin_fondo

        // Configurar e iluminar el icono (este sí se mantiene en Teal)
        ivItemIcon.setImageResource(iconRes)
        ivItemIcon.setColorFilter(
            ContextCompat.getColor(context, R.color.teal_primary),
            android.graphics.PorterDuff.Mode.SRC_IN
        )
        ivItemIcon.visibility = View.VISIBLE

        return view
    }
}