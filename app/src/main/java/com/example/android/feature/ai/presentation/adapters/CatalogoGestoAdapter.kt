package com.example.android.feature.ai.presentation.adapters
import com.example.android.feature.ai.presentation.adapters.CatalogoGestoAdapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.android.R
import com.example.android.core.db.models.CatalogoGesto
import com.google.android.material.materialswitch.MaterialSwitch

class CatalogoGestoAdapter(
    private var gestos: List<CatalogoGesto>,
    private val onGestoToggled: (CatalogoGesto, Boolean) -> Unit
) : RecyclerView.Adapter<CatalogoGestoAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivGestoIcon)
        val tvNombre: TextView = view.findViewById(R.id.tvGestoNombre)
        val switchActivo: MaterialSwitch = view.findViewById(R.id.switchGestoActivo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_catalogo_gesto, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val gesto = gestos[position]
        holder.tvNombre.text = gesto.nombre
        
        // Asignar el icono desde el nombre guardado (ej. "lucide_thumbs_up")
        val iconRes = holder.itemView.context.resources.getIdentifier(
            gesto.icono,
            "drawable",
            holder.itemView.context.packageName
        )
        if (iconRes != 0) {
            holder.ivIcon.setImageResource(iconRes)
        } else {
            holder.ivIcon.setImageResource(R.drawable.ic_lucide_hand)
        }

        // Remover el listener temporalmente para no dispararlo al reciclar
        holder.switchActivo.setOnCheckedChangeListener(null)
        holder.switchActivo.isChecked = gesto.isActive
        
        holder.switchActivo.setOnCheckedChangeListener { _, isChecked ->
            gesto.isActive = isChecked
            onGestoToggled(gesto, isChecked)
        }
    }

    override fun getItemCount() = gestos.size

    fun updateData(newGestos: List<CatalogoGesto>) {
        gestos = newGestos
        notifyDataSetChanged()
    }
}
