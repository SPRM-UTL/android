package com.example.android

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.android.ai.Combo

class GestosAdminAdapter(
    var combos: MutableList<Combo>,
    private val onEditClick: (Combo) -> Unit,
    private val onLongClick: (Combo) -> Unit, // <--- Callback para eliminación
    private val onToggleActive: (Combo, Boolean) -> Unit
) : RecyclerView.Adapter<GestosAdminAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvGestoIcon: TextView = itemView.findViewById(R.id.tvGestoIcon)
        val tvGestoName: TextView = itemView.findViewById(R.id.tvGestoName)
        val tvGestoDevice: TextView = itemView.findViewById(R.id.tvGestoDevice)
        val switchGestoActive: SwitchCompat = itemView.findViewById(R.id.switchGestoActive)

        init {
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onEditClick(combos[adapterPosition])
                }
            }

            // Evento de click largo para detonar el diálogo estilizado
            itemView.setOnLongClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onLongClick(combos[adapterPosition])
                }
                true
            }

            switchGestoActive.setOnCheckedChangeListener { _, isChecked ->
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onToggleActive(combos[adapterPosition], isChecked)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_gesto_admin, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val combo = combos[position]
        holder.tvGestoName.text = combo.name
        holder.tvGestoDevice.text = combo.accionVinculada ?: "Sin acción asignada"

        val iconStr = when {
            combo.name.contains("luz", ignoreCase = true) || combo.accionVinculada?.contains("luz", ignoreCase = true) == true -> "💡"
            combo.name.contains("música", ignoreCase = true) || combo.name.contains("music", ignoreCase = true) -> "🎵"
            combo.name.contains("salud", ignoreCase = true) -> "👋"
            combo.name.contains("tv", ignoreCase = true) -> "📺"
            combo.name.contains("ventilador", ignoreCase = true) -> "🌀"
            else -> "✨"
        }
        holder.tvGestoIcon.text = iconStr

        holder.switchGestoActive.setOnCheckedChangeListener(null)
        holder.switchGestoActive.isChecked = true
        holder.switchGestoActive.setOnCheckedChangeListener { _, isChecked ->
            onToggleActive(combo, isChecked)
        }
    }

    override fun getItemCount(): Int = combos.size
}