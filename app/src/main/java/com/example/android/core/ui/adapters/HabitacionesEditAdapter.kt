package com.example.android.core.ui.adapters
import com.example.android.core.ui.adapters.HabitacionesEditAdapter
import com.example.android.core.db.Habitacion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.android.R

class HabitacionesEditAdapter(
    private val onEditClick: (Habitacion) -> Unit,
    private val onDeleteClick: (Habitacion) -> Unit
) : RecyclerView.Adapter<HabitacionesEditAdapter.ViewHolder>() {

    private var habitaciones: List<Habitacion> = emptyList()

    fun submitList(newList: List<Habitacion>) {
        habitaciones = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_habitacion_edit, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(habitaciones[position])
    }

    override fun getItemCount(): Int = habitaciones.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvNombreHabitacion: TextView = itemView.findViewById(R.id.tvNombreHabitacion)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btnEditHabitacion)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteHabitacion)

        fun bind(habitacion: Habitacion) {
            tvNombreHabitacion.text = habitacion.nombre
            
            btnEdit.setOnClickListener { onEditClick(habitacion) }
            btnDelete.setOnClickListener { onDeleteClick(habitacion) }
        }
    }
}
