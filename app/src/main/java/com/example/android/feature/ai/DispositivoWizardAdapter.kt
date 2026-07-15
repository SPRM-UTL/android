package com.example.android.feature.ai

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.android.R
import com.example.android.core.db.models.Dispositivo

class DispositivoWizardAdapter(
    private val items: List<Dispositivo>,
    private val onSelected: (Dispositivo) -> Unit
) : RecyclerView.Adapter<DispositivoWizardAdapter.ViewHolder>() {

    private var selectedPosition = -1

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    inner class ViewHolder(
        val cardView: com.google.android.material.card.MaterialCardView,
        val ivDeviceIcon: ImageView,
        val tvNombre: TextView,
        val tvTipo: TextView
    ) : RecyclerView.ViewHolder(cardView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context

        val linearLayoutPrincipal = LinearLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val paddingLateral = dpToPx(context, 20)
            val paddingVertical = dpToPx(context, 16)
            setPadding(paddingLateral, paddingVertical, paddingLateral, paddingVertical)
        }

        val sizeInPx = dpToPx(context, 44)
        val imageView = ImageView(context).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(sizeInPx, sizeInPx).apply {
                setMargins(0, 0, dpToPx(context, 16), 0)
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val linearLayoutTextos = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
            orientation = LinearLayout.VERTICAL
        }

        val textViewNombre = TextView(context).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            textSize = 17f
            setTextColor(Color.parseColor("#073F4C"))
        }

        val textViewTipo = TextView(context).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dpToPx(context, 2), 0, 0)
            }
            textSize = 13f
            setTextColor(Color.parseColor("#6F7E8E"))
        }

        linearLayoutTextos.addView(textViewNombre)
        linearLayoutTextos.addView(textViewTipo)

        linearLayoutPrincipal.addView(imageView)
        linearLayoutPrincipal.addView(linearLayoutTextos)

        val cardView = com.google.android.material.card.MaterialCardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                val marginLateral = dpToPx(context, 14)
                val marginVertical = dpToPx(context, 8)
                setMargins(marginLateral, marginVertical, marginLateral, marginVertical)
            }

            radius = dpToPx(context, 18).toFloat()
            cardElevation = dpToPx(context, 3).toFloat()
            maxCardElevation = dpToPx(context, 5).toFloat()
            strokeWidth = 0
            preventCornerOverlap = true
            addView(linearLayoutPrincipal)
        }

        return ViewHolder(cardView, imageView, textViewNombre, textViewTipo)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        holder.tvNombre.text = item.nombre ?: "Dispositivo ${item.id}"

        val tipoOriginal = item.tipo ?: "Desconocido"
        holder.tvTipo.text = tipoOriginal.replaceFirstChar { it.uppercase() }

        val tipoDispositivo = tipoOriginal.lowercase().trim()

        when {
            tipoDispositivo.contains("cámara") ||
                    tipoDispositivo.contains("cam") ||
                    tipoDispositivo.contains("esp32-cam") -> {
                holder.ivDeviceIcon.setImageResource(R.drawable.ic_manordomo_sin_fondo)
            }
            tipoDispositivo.contains("multisocket") ||
                    tipoDispositivo.contains("regleta") ||
                    tipoDispositivo.contains("socket") -> {
                val resId = obtenerResIdDinamico(context, "ic_power")
                    ?: obtenerResIdDinamico(context, "socket")
                    ?: R.drawable.ic_manordomo_sin_fondo
                holder.ivDeviceIcon.setImageResource(resId)
            }
            tipoDispositivo.contains("foco") ||
                    tipoDispositivo.contains("luz") ||
                    tipoDispositivo.contains("lampara") -> {
                val resId = obtenerResIdDinamico(context, "ic_foco")
                    ?: obtenerResIdDinamico(context, "bulb")
                    ?: obtenerResIdDinamico(context, "lightbulb")
                    ?: R.drawable.ic_manordomo_sin_fondo
                holder.ivDeviceIcon.setImageResource(resId)
            }
            tipoDispositivo.contains("ventilador") -> {
                val resId = obtenerResIdDinamico(context, "ic_ventilador")
                    ?: obtenerResIdDinamico(context, "fan")
                    ?: R.drawable.ic_manordomo_sin_fondo
                holder.ivDeviceIcon.setImageResource(resId)
            }
            else -> {
                val tipoSanitizado = tipoDispositivo.replace(" ", "_")
                    .replace("á", "a").replace("é", "e")
                    .replace("í", "i").replace("ó", "o")
                    .replace("ú", "u")
                val resIdDinamico = obtenerResIdDinamico(context, tipoSanitizado)

                if (resIdDinamico != null && resIdDinamico != 0) {
                    holder.ivDeviceIcon.setImageResource(resIdDinamico)
                } else {
                    holder.ivDeviceIcon.setImageResource(R.drawable.ic_manordomo_sin_fondo)
                }
            }
        }

        val iconoColorSelector = ContextCompat.getColorStateList(
            context,
            context.resources.getIdentifier("selector_iconos", "color", context.packageName).takeIf { it != 0 }
                ?: R.color.teal_primary
        )

        if (position == selectedPosition) {
            holder.cardView.setCardBackgroundColor(Color.parseColor("#E0F2F1"))
            holder.cardView.strokeColor = Color.parseColor("#008080")
            holder.cardView.strokeWidth = dpToPx(context, 1)
            holder.tvNombre.setTypeface(holder.tvNombre.typeface, android.graphics.Typeface.BOLD)

            holder.ivDeviceIcon.imageTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.teal_primary)
            )
        } else {
            holder.cardView.setCardBackgroundColor(Color.parseColor("#FFFFFF"))
            holder.cardView.strokeWidth = 0
            holder.tvNombre.typeface = android.graphics.Typeface.DEFAULT

            holder.ivDeviceIcon.imageTintList = iconoColorSelector
        }

        holder.itemView.setOnClickListener {
            val previousSelected = selectedPosition
            selectedPosition = holder.adapterPosition

            notifyItemChanged(previousSelected)
            notifyItemChanged(selectedPosition)

            onSelected(item)
        }
    }

    override fun getItemCount() = items.size

    private fun obtenerResIdDinamico(context: Context, nombreVariable: String): Int? {
        val id = context.resources.getIdentifier(nombreVariable, "drawable", context.packageName)
        return if (id != 0) id else null
    }
}
