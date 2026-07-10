package com.example.android.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.android.R
import com.google.android.material.card.MaterialCardView

class WizardSelectionAdapter(
    private val items: List<String>,
    private val iconProvider: (String) -> Int?,
    private var selectedItem: String,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<WizardSelectionAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardWizardItem)
        val icon: ImageView = view.findViewById(R.id.ivWizardItemIcon)
        val text: TextView = view.findViewById(R.id.tvWizardItemText)

        init {
            card.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val previousSelected = selectedItem
                    selectedItem = items[position]
                    
                    val previousIndex = items.indexOf(previousSelected)
                    if (previousIndex != -1) notifyItemChanged(previousIndex)
                    notifyItemChanged(position)
                    
                    onItemClick(selectedItem)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wizard_selection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.text.text = item

        val iconRes = iconProvider(item)
        if (iconRes != null) {
            holder.icon.setImageResource(iconRes)
            holder.icon.visibility = View.VISIBLE
        } else {
            holder.icon.visibility = View.GONE
        }

        if (item == selectedItem) {
            holder.card.strokeColor = Color.parseColor("#3aafa9") // teal_primary
            holder.card.strokeWidth = (2 * holder.itemView.context.resources.displayMetrics.density).toInt()
            holder.card.setCardBackgroundColor(Color.parseColor("#E0F2F1")) // very light teal
            holder.text.setTextColor(Color.parseColor("#3aafa9"))
            holder.icon.setColorFilter(Color.parseColor("#3aafa9"))
        } else {
            holder.card.strokeColor = Color.parseColor("#E0E0E0")
            holder.card.strokeWidth = (2 * holder.itemView.context.resources.displayMetrics.density).toInt()
            holder.card.setCardBackgroundColor(Color.WHITE)
            holder.text.setTextColor(Color.parseColor("#073F4C"))
            holder.icon.setColorFilter(Color.parseColor("#6F7E8E"))
        }
    }

    override fun getItemCount() = items.size
}
