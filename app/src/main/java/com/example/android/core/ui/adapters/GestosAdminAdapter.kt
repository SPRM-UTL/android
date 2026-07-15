package com.example.android.core.ui.adapters

import com.example.android.R

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.android.feature.ai.Combo

class GestosAdminAdapter(
    var combos: MutableList<Combo>,
    private val onEditClick: (Combo) -> Unit,
    private val onLongClick: (Combo) -> Unit // <--- Callback para eliminación
) : RecyclerView.Adapter<GestosAdminAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivGestoIcon: android.widget.ImageView = itemView.findViewById(R.id.ivGestoIcon)
        val tvGestoName: TextView = itemView.findViewById(R.id.tvGestoName)
        val tvGestoDevice: TextView = itemView.findViewById(R.id.tvGestoDevice)

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

        val iconName = combo.icono ?: "lucide_star"
        val context = holder.itemView.context
        val resId = context.resources.getIdentifier(iconName, "drawable", context.packageName)
        if (resId != 0) {
            holder.ivGestoIcon.setImageResource(resId)
        } else {
            holder.ivGestoIcon.setImageResource(R.drawable.lucide_star)
        }
    }

    override fun getItemCount(): Int = combos.size
}
