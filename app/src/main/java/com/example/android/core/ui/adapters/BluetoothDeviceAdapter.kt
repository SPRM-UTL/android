package com.example.android.core.ui.adapters
import com.example.android.core.ui.adapters.BluetoothDeviceAdapter
import com.example.android.core.network.ResultadoDispositivoBt

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.android.R

class BluetoothDeviceAdapter(
    private val alSeleccionar: (ResultadoDispositivoBt) -> Unit
) : RecyclerView.Adapter<BluetoothDeviceAdapter.VistaBtHolder>() {

    private val elementos = mutableListOf<ResultadoDispositivoBt>()

    fun agregarDispositivo(dispositivo: ResultadoDispositivoBt) {
        val index = elementos.indexOfFirst { it.mac == dispositivo.mac }
        if (index == -1) {
            elementos.add(dispositivo)
            notifyItemInserted(elementos.size - 1)
        } else {
            // Si ya existe pero el nuevo tiene un nombre mejor (que no sea "Desconocido")
            val actual = elementos[index]
            if (actual.nombre.contains("desconocido", ignoreCase = true) && !dispositivo.nombre.contains("desconocido", ignoreCase = true)) {
                elementos[index] = dispositivo
                notifyItemChanged(index)
            }
        }
    }

    fun limpiar() {
        elementos.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VistaBtHolder {
        val vista = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bluetooth_device, parent, false)
        return VistaBtHolder(vista)
    }

    override fun onBindViewHolder(holder: VistaBtHolder, position: Int) {
        holder.vincular(elementos[position])
    }

    override fun getItemCount() = elementos.size

    inner class VistaBtHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvNombre: TextView = itemView.findViewById(R.id.tvBtDeviceName)
        private val tvMac: TextView    = itemView.findViewById(R.id.tvBtDeviceMac)
        private val btnConectar: View  = itemView.findViewById(R.id.btnItemConectar)

        fun vincular(dispositivo: ResultadoDispositivoBt) {
            tvNombre.text = dispositivo.nombre
            tvMac.text    = "Bluetooth"
            
            // Hacer que toda la tarjeta o el botón lancen la acción
            itemView.setOnClickListener { alSeleccionar(dispositivo) }
            btnConectar.setOnClickListener { alSeleccionar(dispositivo) }
        }
    }
}
