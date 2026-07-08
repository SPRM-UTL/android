package com.example.android.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.android.R

class AddGestoAdapter(private val onAddClick: () -> Unit) :
    RecyclerView.Adapter<AddGestoAdapter.AddViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AddViewHolder {
        // Reutilizamos el layout item_device de tipo "agregar" o creas uno idéntico llamado item_add_gesto si prefieres cambiar los textos internamente.
        // Asumiendo que puedes usar el mismo estilo visual:
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_add_device, parent, false)
        return AddViewHolder(view)
    }

    override fun onBindViewHolder(holder: AddViewHolder, position: Int) {
        holder.itemView.setOnClickListener { onAddClick() }
    }

    override fun getItemCount(): Int = 1

    class AddViewHolder(view: View) : RecyclerView.ViewHolder(view)
}