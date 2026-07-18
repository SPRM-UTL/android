package com.example.android.feature.ai.presentation.adapters
import com.example.android.feature.ai.presentation.adapters.DispositivoPagerAdapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.android.R

class DispositivoPagerAdapter(
    private val onStepBound: (Int, View) -> Unit
) : RecyclerView.Adapter<DispositivoPagerAdapter.StepViewHolder>() {

    class StepViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun getItemCount(): Int = 2

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val layoutRes = when (viewType) {
            0 -> R.layout.step_wizard_dispositivo_lista
            else -> R.layout.step_wizard_dispositivo_accion
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return StepViewHolder(view)
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        onStepBound(position, holder.itemView)
    }

    override fun getItemViewType(position: Int): Int = position
}
