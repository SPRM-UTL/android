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
import com.example.android.db.Dispositivo
import com.google.android.material.switchmaterial.SwitchMaterial

class DeviceAdapter(
    private val onEditClick: (Dispositivo) -> Unit,
    private val onDeleteClick: (Dispositivo) -> Unit,
    private val onToggleClick: (Dispositivo, Boolean) -> Unit
) : ListAdapter<Dispositivo, DeviceAdapter.DeviceViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvDeviceName)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvDeviceStatus)
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivDeviceIcon)
        private val switchDevice: SwitchMaterial = itemView.findViewById(R.id.switchDevice)
        
        private val cardView: View = itemView.findViewById(R.id.deviceCard)

        fun bind(dispositivo: Dispositivo) {
            tvName.text = dispositivo.nombre
            tvStatus.text = dispositivo.tipo ?: "Dispositivo"
            
            ivIcon.setImageResource(android.R.drawable.ic_lock_power_off)

            switchDevice.setOnCheckedChangeListener { _, isChecked ->
                onToggleClick(dispositivo, isChecked)
            }

            cardView.setOnLongClickListener {
                // Trigger edit/delete menu
                // Normally a PopupMenu is shown here.
                // For simplicity, let's call edit on click and delete on long click?
                // Or let the Activity handle the PopupMenu by passing the view.
                true
            }
            
            cardView.setOnClickListener {
                onEditClick(dispositivo)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Dispositivo>() {
            override fun areItemsTheSame(oldItem: Dispositivo, newItem: Dispositivo): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Dispositivo, newItem: Dispositivo): Boolean {
                return oldItem == newItem
            }
        }
    }
}
