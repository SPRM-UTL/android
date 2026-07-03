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

    private var connectedMacs: Set<String> = emptySet()
    private val deviceStates: MutableMap<Int, Boolean> = mutableMapOf()

    fun actualizarEstados(macs: Set<String>) {
        connectedMacs = macs
        notifyDataSetChanged()
    }

    fun sincronizarEstadosDesdeDispositivos(dispositivos: List<Dispositivo>) {
        dispositivos.forEach { dispositivo ->
            dispositivo.estadoEncendido?.let { encendido ->
                deviceStates[dispositivo.id] = encendido
            }
        }
        notifyDataSetChanged()
    }

    fun actualizarEstadoDispositivo(id: Int, encendido: Boolean) {
        deviceStates[id] = encendido
        val index = currentList.indexOfFirst { it.id == id }
        if (index >= 0) notifyItemChanged(index)
    }

    fun isDeviceConnected(mac: String?): Boolean {
        if (mac.isNullOrBlank()) return false
        return connectedMacs.any { it.equals(mac, ignoreCase = true) }
    }

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
        private val statusDot: com.google.android.material.card.MaterialCardView = itemView.findViewById(R.id.statusDot)
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivDeviceIcon)
        private val switchDevice: SwitchMaterial = itemView.findViewById(R.id.switchDevice)
        private val cardView: View = itemView.findViewById(R.id.deviceCard)

        fun bind(dispositivo: Dispositivo) {
            tvName.text = dispositivo.nombre
            
            val isConnected = connectedMacs.any {
                it.equals(dispositivo.macBluetooth, ignoreCase = true)
            }
            if (isConnected) {
                tvStatus.text = "En línea"
                tvStatus.setTextColor(android.graphics.Color.parseColor("#009688"))
                statusDot.setCardBackgroundColor(android.graphics.Color.parseColor("#009688"))
            } else {
                tvStatus.text = "Desconectado"
                tvStatus.setTextColor(android.graphics.Color.parseColor("#6F7EA8"))
                statusDot.setCardBackgroundColor(android.graphics.Color.parseColor("#6F7EA8"))
            }
            
            ivIcon.setImageResource(R.drawable.ic_power)

            val isOn = deviceStates[dispositivo.id] ?: dispositivo.estadoEncendido ?: false
            switchDevice.setOnCheckedChangeListener(null)
            switchDevice.isChecked = isOn
            switchDevice.isEnabled = isConnected
            switchDevice.alpha = if (isConnected) 1f else 0.5f

            val iconBg = itemView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.iconBg)
            if (isOn) {
                iconBg.setCardBackgroundColor(android.graphics.Color.parseColor("#009688"))
            } else {
                iconBg.setCardBackgroundColor(android.graphics.Color.parseColor("#B0BEC5"))
            }

            switchDevice.setOnCheckedChangeListener { _, isChecked ->
                if (!isConnected) return@setOnCheckedChangeListener
                deviceStates[dispositivo.id] = isChecked
                onToggleClick(dispositivo, isChecked)
                if (isChecked) {
                    iconBg.setCardBackgroundColor(android.graphics.Color.parseColor("#009688"))
                } else {
                    iconBg.setCardBackgroundColor(android.graphics.Color.parseColor("#B0BEC5"))
                }
            }

            cardView.setOnLongClickListener {
                onDeleteClick(dispositivo)
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
