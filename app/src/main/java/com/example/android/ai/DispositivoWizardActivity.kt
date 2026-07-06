package com.example.android.ai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.android.R
import com.example.android.db.AppDatabase
import com.example.android.db.Dispositivo
import kotlinx.coroutines.launch

class DispositivoWizardActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var rvDispositivos: RecyclerView
    private lateinit var rgAcciones: RadioGroup
    private lateinit var btnFinalizar: Button
    private lateinit var mainWizardLayout: ConstraintLayout

    private var dispositivoSeleccionado: Dispositivo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = Color.TRANSPARENT

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightNavigationBars = true
            isAppearanceLightStatusBars = false
        }

        setContentView(R.layout.activity_dispositivo_wizard)

        db = AppDatabase.getDatabase(this)

        mainWizardLayout = findViewById(R.id.mainWizardLayout)
        rvDispositivos = findViewById(R.id.rvDispositivosWizard)
        rgAcciones = findViewById(R.id.rgAcciones)
        btnFinalizar = findViewById(R.id.btnFinalizarWizard)

        ViewCompat.setOnApplyWindowInsetsListener(mainWizardLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom + ime.bottom)

            val cardBack = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardBack)
            cardBack?.getChildAt(0)?.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        rvDispositivos.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            val listaDispositivos = db.dispositivoDao().getAllDispositivosOnce()
            if (listaDispositivos.isEmpty()) {
                Toast.makeText(this@DispositivoWizardActivity, "No tienes dispositivos agregados.", Toast.LENGTH_LONG).show()
            } else {
                rvDispositivos.adapter = DispositivoSimpleAdapter(listaDispositivos) { dispositivo ->
                    dispositivoSeleccionado = dispositivo
                }
            }
        }

        btnFinalizar.setOnClickListener {
            val disp = dispositivoSeleccionado
            if (disp == null) {
                Toast.makeText(this, "Por favor, selecciona un dispositivo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val tipoAccion = when (rgAcciones.checkedRadioButtonId) {
                R.id.rbEncender -> 0
                R.id.rbApagar -> 1
                else -> 2
            }

            val resultIntent = Intent().apply {
                putExtra("DISPOSITIVO_ID", disp.id)
                putExtra("DISPOSITIVO_NOMBRE", disp.nombre ?: "Dispositivo ${disp.id}")
                putExtra("ACCION_TIPO", tipoAccion)
            }

            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    private inner class DispositivoSimpleAdapter(
        private val items: List<Dispositivo>,
        private val onSelected: (Dispositivo) -> Unit
    ) : RecyclerView.Adapter<DispositivoSimpleAdapter.ViewHolder>() {

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

            // SE MODIFICÓ AQUÍ: Eliminamos el prefijo "Tipo: " para mostrar el texto limpio
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

            // --- APLICACIÓN DE LA PALETA DE COLORES (TINT) ---
            val iconoColorSelector = ContextCompat.getColorStateList(
                context,
                context.resources.getIdentifier("selector_iconos", "color", context.packageName).takeIf { it != 0 }
                    ?: R.color.teal_primary
            )

            if (position == selectedPosition) {
                holder.cardView.setCardBackgroundColor(Color.parseColor("#E0F2F1"))
                holder.cardView.strokeColor = Color.parseColor("#008080")
                holder.cardView.strokeWidth = dpToPx(context, 1)
                holder.tvNombre.textStyleBold()

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

        private fun TextView.textStyleBold() {
            this.setTypeface(this.typeface, android.graphics.Typeface.BOLD)
        }
    }
}