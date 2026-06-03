package com.example.android.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.android.R
import com.example.android.network.ResultadoDispositivoBt

class BluetoothDeviceAdapter(
    private val alSeleccionar: (ResultadoDispositivoBt) -> Unit
) : RecyclerView.Adapter<BluetoothDeviceAdapter.VistaBtHolder>() {

    private val elementos = mutableListOf<ResultadoDispositivoBt>()

    fun agregarDispositivo(dispositivo: ResultadoDispositivoBt) {
        if (elementos.none { it.mac == dispositivo.mac }) {
            elementos.add(dispositivo)
            notifyItemInserted(elementos.size - 1)
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

        fun vincular(dispositivo: ResultadoDispositivoBt) {
            tvNombre.text = dispositivo.nombre
            tvMac.text    = dispositivo.mac
            itemView.setOnClickListener { alSeleccionar(dispositivo) }
        }
    }
}
