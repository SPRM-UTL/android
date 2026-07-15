package com.example.android.core.ui.adapters

import com.example.android.R

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.android.core.db.AparatoTipo

class AparatoTipoAdapter(
    private var tipos: List<AparatoTipo>,
    private val onTipoClick: (AparatoTipo) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HERO = 0
        private const val VIEW_TYPE_NORMAL = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (tipos[position].esAsistente) VIEW_TYPE_HERO else VIEW_TYPE_NORMAL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HERO) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tipo_hero, parent, false)
            HeroViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tipo_normal, parent, false)
            NormalViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val tipo = tipos[position]
        
        var iconName = tipo.icono
        if (iconName.isNullOrEmpty()) {
            iconName = when (tipo.nombreTipo) {
                "Audífonos" -> "headphones"
                "Bocinas" -> "speaker"
                "Focos" -> "lightbulb"
                "Luces" -> "lamp_floor"
                "Ventilador" -> "wind"
                "Televisión" -> "tv_minimal"
                "Sockets Inteligentes" -> "plug"
                "MultiSocket" -> "plug"
                "Asistente" -> "ic_input_add"
                else -> "ic_menu_camera"
            }
        }
        
        val cleanIconName = iconName.substringBeforeLast(".").replace("-", "_")

        // Obtener ID del recurso de forma dinámica por nombre
        val resId = holder.itemView.context.resources.getIdentifier(
            cleanIconName,
            "drawable",
            holder.itemView.context.packageName
        )

        val finalResId = if (resId != 0) {
            resId
        } else {
            when (tipo.nombreTipo) {
                "Audífonos" -> R.drawable.headphones
                "Bocinas" -> R.drawable.speaker
                "Focos" -> R.drawable.lightbulb
                "Luces" -> R.drawable.lamp_floor
                "Ventilador" -> R.drawable.wind
                "Televisión" -> R.drawable.tv_minimal
                "Sockets Inteligentes", "Enchufe", "MultiSocket" -> R.drawable.plug
                "Cámaras", "Cámara" -> R.drawable.camera
                "Asistente" -> R.drawable.ic_input_add
                else -> android.R.drawable.ic_menu_camera
            }
        }

        if (holder is HeroViewHolder) {
            holder.title.text = tipo.nombreTipo
            holder.icon.setImageResource(finalResId)
            
            holder.wifiIcon.visibility = if (tipo.soportaWifi) View.VISIBLE else View.GONE
            holder.bluetoothIcon.visibility = if (tipo.soportaBluetooth) View.VISIBLE else View.GONE
            holder.linkIcon.visibility = if (tipo.requiereVinculacionBluetooth) View.VISIBLE else View.GONE

            holder.itemView.setOnClickListener { onTipoClick(tipo) }
        } else if (holder is NormalViewHolder) {
            holder.title.text = tipo.nombreTipo
            holder.icon.setImageResource(finalResId)

            holder.wifiIcon.visibility = if (tipo.soportaWifi) View.VISIBLE else View.GONE
            holder.bluetoothIcon.visibility = if (tipo.soportaBluetooth) View.VISIBLE else View.GONE
            holder.linkIcon.visibility = if (tipo.requiereVinculacionBluetooth) View.VISIBLE else View.GONE

            holder.itemView.setOnClickListener { onTipoClick(tipo) }
        }
    }

    override fun getItemCount(): Int = tipos.size

    fun submitList(newList: List<AparatoTipo>) {
        tipos = newList
        notifyDataSetChanged()
    }

    class HeroViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvHeroTitle)
        val icon: ImageView = view.findViewById(R.id.ivIconHero)
        val wifiIcon: ImageView = view.findViewById(R.id.ivHeroSupportWifi)
        val bluetoothIcon: ImageView = view.findViewById(R.id.ivHeroSupportBluetooth)
        val linkIcon: ImageView = view.findViewById(R.id.ivHeroSupportLink)
    }

    class NormalViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvTitle)
        val icon: ImageView = view.findViewById(R.id.ivIcon)
        val wifiIcon: ImageView = view.findViewById(R.id.ivSupportWifi)
        val bluetoothIcon: ImageView = view.findViewById(R.id.ivSupportBluetooth)
        val linkIcon: ImageView = view.findViewById(R.id.ivSupportLink)
    }
}
