package com.example.android.core.ui.adapters
import com.example.android.core.ui.adapters.WifiDeviceAdapter

import android.net.wifi.ScanResult
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.android.R

class WifiDeviceAdapter(
    private val alSeleccionar: (ScanResult) -> Unit
) : RecyclerView.Adapter<WifiDeviceAdapter.VistaWifiHolder>() {

    private val elementos = mutableListOf<ScanResult>()

    fun agregarDispositivo(dispositivo: ScanResult) {
        val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            dispositivo.wifiSsid?.toString() ?: dispositivo.SSID
        } else {
            dispositivo.SSID
        }
        val cleanSsid = ssid?.removePrefix("\"")?.removeSuffix("\"")
        
        if (!cleanSsid.isNullOrEmpty() && elementos.none { getSsid(it) == cleanSsid }) {
            elementos.add(dispositivo)
            notifyItemInserted(elementos.size - 1)
        }
    }

    private fun getSsid(dispositivo: ScanResult): String {
        val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            dispositivo.wifiSsid?.toString() ?: dispositivo.SSID
        } else {
            dispositivo.SSID
        }
        return ssid?.removePrefix("\"")?.removeSuffix("\"") ?: ""
    }

    fun limpiar() {
        elementos.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VistaWifiHolder {
        val vista = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bluetooth_device, parent, false)
        return VistaWifiHolder(vista)
    }

    override fun onBindViewHolder(holder: VistaWifiHolder, position: Int) {
        holder.vincular(elementos[position])
    }

    override fun getItemCount() = elementos.size

    inner class VistaWifiHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvNombre: TextView = itemView.findViewById(R.id.tvBtDeviceName)
        private val tvMac: TextView    = itemView.findViewById(R.id.tvBtDeviceMac)
        private val btnConectar: View  = itemView.findViewById(R.id.btnItemConectar)


        fun vincular(dispositivo: ScanResult) {
            tvNombre.text = getSsid(dispositivo)
            tvMac.text    = "Wi-Fi (${dispositivo.level} dBm)"
            
            itemView.setOnClickListener { alSeleccionar(dispositivo) }
            btnConectar.setOnClickListener { alSeleccionar(dispositivo) }
        }
    }
}
