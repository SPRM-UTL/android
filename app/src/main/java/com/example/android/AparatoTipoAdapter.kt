package com.example.android

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.android.db.AparatoTipo

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
                "Asistente" -> "ic_input_add"
                else -> "ic_menu_camera"
            }
        }

        // Obtener ID del recurso de forma dinámica por nombre
        val resId = holder.itemView.context.resources.getIdentifier(
            iconName,
            "drawable",
            holder.itemView.context.packageName
        )

        // Si no lo encuentra en drawable, intenta en mipmap o usa uno por defecto
        val finalResId = if (resId != 0) resId else android.R.drawable.ic_menu_camera

        if (holder is HeroViewHolder) {
            holder.title.text = tipo.nombreTipo
            holder.icon.setImageResource(finalResId)
            
            holder.wifiIcon.visibility = if (tipo.soportaWifi) View.VISIBLE else View.GONE
            holder.bluetoothIcon.visibility = if (tipo.soportaBluetooth) View.VISIBLE else View.GONE

            holder.itemView.setOnClickListener { onTipoClick(tipo) }
        } else if (holder is NormalViewHolder) {
            holder.title.text = tipo.nombreTipo
            holder.icon.setImageResource(finalResId)

            holder.wifiIcon.visibility = if (tipo.soportaWifi) View.VISIBLE else View.GONE
            holder.bluetoothIcon.visibility = if (tipo.soportaBluetooth) View.VISIBLE else View.GONE

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
    }

    class NormalViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvTitle)
        val icon: ImageView = view.findViewById(R.id.ivIcon)
        val wifiIcon: ImageView = view.findViewById(R.id.ivSupportWifi)
        val bluetoothIcon: ImageView = view.findViewById(R.id.ivSupportBluetooth)
    }
}
