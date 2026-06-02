package com.example.android.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.android.R
import com.example.android.db.Gesto
import com.google.android.material.switchmaterial.SwitchMaterial

class GestureAdapter(
    private val getDeviceName: (Int?) -> String,
    private val onEditClick: (Gesto) -> Unit,
    private val onDeleteClick: (Gesto) -> Unit,
    private val onToggleClick: (Gesto, Boolean) -> Unit
) : ListAdapter<Gesto, GestureAdapter.GestureViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GestureViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_gesture_active, parent, false)
        return GestureViewHolder(view)
    }

    override fun onBindViewHolder(holder: GestureViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class GestureViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvGestureName)
        private val tvDesc: TextView = itemView.findViewById(R.id.tvGestureDesc)
        // private val tvTarget: TextView? = itemView.findViewById(R.id.tvDeviceTarget)
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivGestureIcon)
        private val switchGesture: SwitchMaterial = itemView.findViewById(R.id.switchGesture)
        
        fun bind(gesto: Gesto) {
            tvName.text = gesto.nombre
            tvDesc.text = getDeviceName(gesto.aparatoId)
            // tvTarget?.text = "Dispositivo ID: ${gesto.aparatoId}" // Muestra id por ahora

            ivIcon.setImageResource(android.R.drawable.ic_menu_camera)

            switchGesture.setOnCheckedChangeListener { _, isChecked ->
                onToggleClick(gesto, isChecked)
            }

            itemView.setOnClickListener {
                onEditClick(gesto)
            }
            
            itemView.setOnLongClickListener {
                onDeleteClick(gesto)
                true
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Gesto>() {
            override fun areItemsTheSame(oldItem: Gesto, newItem: Gesto): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Gesto, newItem: Gesto): Boolean {
                return oldItem == newItem
            }
        }
    }
}
