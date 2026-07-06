package com.example.android.ai

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

    private var dispositivoSeleccionado: Dispositivo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dispositivo_wizard)

        db = AppDatabase.getDatabase(this)

        rvDispositivos = findViewById(R.id.rvDispositivosWizard)
        rgAcciones = findViewById(R.id.rgAcciones)
        btnFinalizar = findViewById(R.id.btnFinalizarWizard)

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

        inner class ViewHolder(val tv: TextView) : RecyclerView.ViewHolder(tv)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val textView = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(48, 36, 48, 36)
                // SOLUCIÓN: Dejar una sola asignación float limpia (Android la toma como SP por defecto)
                textSize = 16f
                setTextColor(Color.parseColor("#073F4C"))
            }
            return ViewHolder(textView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tv.text = item.nombre ?: "Dispositivo ${item.id}"

            if (position == selectedPosition) {
                holder.tv.setBackgroundColor(Color.parseColor("#E0F2F1"))
                holder.tv.textStyleBold()
            } else {
                holder.tv.setBackgroundColor(Color.TRANSPARENT)
                holder.tv.typeface = android.graphics.Typeface.DEFAULT
            }

            holder.itemView.setOnClickListener {
                notifyItemChanged(selectedPosition)
                selectedPosition = holder.adapterPosition
                notifyItemChanged(selectedPosition)
                onSelected(item)
            }
        }

        override fun getItemCount() = items.size

        private fun TextView.textStyleBold() {
            this.setTypeface(this.typeface, android.graphics.Typeface.BOLD)
        }
    }
}