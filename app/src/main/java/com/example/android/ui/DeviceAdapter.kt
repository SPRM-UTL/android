package com.example.android.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
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
        private val tvName: TextView     = itemView.findViewById(R.id.tvDeviceName)
        private val tvStatus: TextView   = itemView.findViewById(R.id.tvDeviceStatus)
        private val ivIcon: ImageView    = itemView.findViewById(R.id.ivDeviceIcon)
        private val switchDevice: SwitchMaterial = itemView.findViewById(R.id.switchDevice)
        private val cardView: View       = itemView.findViewById(R.id.deviceCard)

        fun bind(dispositivo: Dispositivo) {
            tvName.text   = dispositivo.nombre
            tvStatus.text = dispositivo.tipo ?: "Dispositivo"

            // ── Ícono dinámico por tipo de dispositivo ─────────────────
            //
            // Estrategia:
            // 1. Intentar mapear `icono` (nombre Lucide del servidor) a drawable local
            // 2. Si no matchea, mapear `tipo` a drawable local
            // 3. Siempre aplicar tint blanco por código (Coil lo borraría si cargara del CDN)
            //
            // NOTA: no usamos LucideLoader/CDN aquí porque Coil elimina el
            // ImageTintList al reemplazar el drawable, dejando el ícono invisible
            // sobre el fondo teal. Todos los íconos viven como drawables locales.

            val iconoServidor = dispositivo.icono?.trim()

            val drawableRes = if (!iconoServidor.isNullOrEmpty()) {
                // Prioridad 1: mapear nombre Lucide del servidor a drawable local
                val localPorIcono = iconoNombreALocal(iconoServidor)
                // Si no hay match local, caer al mapa por tipo
                if (localPorIcono != null) localPorIcono else iconoPorTipo(dispositivo.tipo)
            } else {
                // Prioridad 2: tipo de dispositivo → drawable local
                iconoPorTipo(dispositivo.tipo)
            }

            ivIcon.setImageResource(drawableRes)
            // Aplicar tint blanco por código para garantizar visibilidad
            // (evita el problema donde app:tint en XML se pierde al cambiar el src)
            ImageViewCompat.setImageTintList(ivIcon, ColorStateList.valueOf(Color.WHITE))

            switchDevice.setOnCheckedChangeListener { _, isChecked ->
                onToggleClick(dispositivo, isChecked)
            }

            cardView.setOnLongClickListener { 
                onDeleteClick(dispositivo)
                true 
            }
            cardView.setOnClickListener { onEditClick(dispositivo) }
        }
    }

    companion object {

        /**
         * Mapea el nombre de ícono Lucide que envía el servidor al drawable local equivalente.
         * Cubre los nombres más comunes de la biblioteca Lucide Icons.
         */
        /**
         * Mapea el nombre de ícono Lucide que envía el servidor al drawable local equivalente.
         * Retorna null si no hay match local (el caller usará iconoPorTipo como fallback).
         */
        private fun iconoNombreALocal(nombre: String): Int? = when (nombre.trim().lowercase()) {
            "lightbulb", "lightbulb-off",
            "lamp", "lamp-floor",
            "lamp-ceiling", "lamp-desk"      -> R.drawable.lightbulb

            "fan"                            -> R.drawable.fan

            "tv", "tv-2", "tv-minimal",
            "monitor", "screen-share"        -> R.drawable.tv_minimal

            "camera", "cctv",
            "camera-off", "webcam"           -> R.drawable.camera

            "speaker", "headphones",
            "music", "volume-2",
            "radio"                          -> R.drawable.speaker

            "plug", "plug-2",
            "plug-zap"                       -> R.drawable.plug

            "lock", "lock-open",
            "lock-keyhole", "door-closed",
            "door-open"                      -> R.drawable.lock

            "wind", "thermometer",
            "cloud", "droplets",
            "sun-snow"                       -> R.drawable.wind

            "bluetooth", "bluetooth-connected",
            "bluetooth-searching"            -> R.drawable.bluetooth

            "wifi", "wifi-off",
            "wifi-high", "router"            -> R.drawable.wifi

            "power", "power-off",
            "zap", "zap-off"                 -> R.drawable.power

            else                             -> null
        }

        /**
         * Mapea el campo `tipo` del dispositivo a un drawable local.
         * Se usa cuando `icono` es nulo/vacío o no matchea en [iconoNombreALocal].
         */
        fun iconoPorTipo(tipo: String?): Int = when (tipo?.trim()?.lowercase()) {
            "foco", "luz", "bombilla",
            "lightbulb", "light",
            "lámpara", "lampara"             -> R.drawable.lightbulb

            "ventilador", "fan",
            "aire", "climatizador",
            "ventiladores"                   -> R.drawable.fan

            "televisión", "television",
            "tv", "pantalla", "televisor"    -> R.drawable.tv_minimal

            "cámara", "camara",
            "camera", "cctv", "seguridad",
            "cámaras", "camaras"             -> R.drawable.camera

            "bocina", "bocinas", "speaker",
            "audio", "altavoz", "sonido",
            "altavoces"                      -> R.drawable.speaker

            "enchufe", "enchufes", "plug",
            "socket", "tomacorriente"        -> R.drawable.plug

            "cerradura", "cerraduras",
            "lock", "puerta", "puertas"      -> R.drawable.lock

            "sensor", "sensores", "wind",
            "clima", "temperatura"           -> R.drawable.wind

            "bluetooth"                      -> R.drawable.bluetooth

            "wifi", "red", "router"          -> R.drawable.wifi

            else                             -> R.drawable.power
        }

        private val DiffCallback = object : DiffUtil.ItemCallback<Dispositivo>() {
            override fun areItemsTheSame(oldItem: Dispositivo, newItem: Dispositivo): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Dispositivo, newItem: Dispositivo): Boolean =
                oldItem == newItem
        }
    }
}
