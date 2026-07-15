package com.example.android.core.ui.adapters
import com.example.android.core.ui.adapters.IconPickerAdapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.android.R

class IconPickerAdapter(
    private val allIcons: List<String>,
    private val onIconSelected: (String) -> Unit
) : RecyclerView.Adapter<IconPickerAdapter.IconViewHolder>() {

    private var filteredIcons = allIcons.toList()

    inner class IconViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivIcon: ImageView = itemView.findViewById(R.id.ivIcon)

        init {
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onIconSelected(filteredIcons[adapterPosition])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_icon_grid, parent, false)
        return IconViewHolder(view)
    }

    override fun onBindViewHolder(holder: IconViewHolder, position: Int) {
        val iconName = filteredIcons[position]
        val context = holder.itemView.context
        val resId = context.resources.getIdentifier(iconName, "drawable", context.packageName)
        if (resId != 0) {
            holder.ivIcon.setImageResource(resId)
        } else {
            holder.ivIcon.setImageResource(R.drawable.ic_manordomo_sin_fondo)
        }
    }

    override fun getItemCount(): Int = filteredIcons.size

    fun filter(query: String) {
        filteredIcons = if (query.isEmpty()) {
            allIcons
        } else {
            allIcons.filter { it.contains(query, ignoreCase = true) }
        }
        notifyDataSetChanged()
    }
}
