package com.example.android.ai
import com.example.android.R

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SecuenciaAdapter(
    var pasos: MutableList<PasoSecuencia>,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onClickStep: (Int) -> Unit
) : RecyclerView.Adapter<SecuenciaAdapter.StepViewHolder>() {

    inner class StepViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvStepNumber: TextView = itemView.findViewById(R.id.tvStepNumber)
        val tvPoseName: TextView = itemView.findViewById(R.id.tvStepPose)
        val tvStepHand: TextView = itemView.findViewById(R.id.tvStepHand)
        val tvStepFrames: TextView = itemView.findViewById(R.id.tvStepFrames)
        val ivDragHandle: ImageView = itemView.findViewById(R.id.ivDragHandle)

        init {
            itemView.setOnClickListener {
                onClickStep(adapterPosition)
            }
            ivDragHandle.setOnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                    onStartDrag(this)
                }
                false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sequence_step, parent, false)
        return StepViewHolder(view)
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        val step = pasos[position]
        holder.tvStepNumber.text = "${position + 1}."
        holder.tvPoseName.text = step.nombreGesto
        
        val handStr = when (step.manoObjetivo) {
            ManoObjetivo.LEFT -> "Mano Izquierda"
            ManoObjetivo.RIGHT -> "Mano Derecha"
            ManoObjetivo.ANY -> "Cualquier Mano"
        }
        holder.tvStepHand.text = "Mano: $handStr"
        holder.tvStepFrames.text = "${step.cuadrosRequeridos}f"
    }

    override fun getItemCount(): Int = pasos.size

    fun moveItem(fromPosition: Int, toPosition: Int) {
        val step = pasos.removeAt(fromPosition)
        pasos.add(toPosition, step)
        notifyItemMoved(fromPosition, toPosition)
        // Update all step numbers
        notifyItemRangeChanged(0, pasos.size)
    }

    fun removeItem(position: Int) {
        pasos.removeAt(position)
        notifyItemRemoved(position)
        // Update all step numbers
        notifyItemRangeChanged(0, pasos.size)
    }
}
