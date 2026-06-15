package com.example.android.ai
import com.example.android.R

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class SecuenciaConfigActivity : AppCompatActivity() {

    private lateinit var rvSteps: RecyclerView
    private lateinit var etComboName: EditText
    private lateinit var tvActivatorDesc: TextView
    private lateinit var btnEditActivator: Button
    private lateinit var tvAccionDesc: TextView
    private lateinit var btnEditAccion: Button
    private lateinit var btnSave: Button
    private lateinit var fabAdd: FloatingActionButton
    
    private lateinit var adapter: SecuenciaAdapter
    
    private var todosCombos: MutableList<Combo> = mutableListOf()
    private lateinit var comboActual: Combo
    private var comboIndex: Int = -1

    private val wizardLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val nombreGesto = data.getStringExtra("POSE_NAME") ?: return@registerForActivityResult
            val manoObjetivoStr = data.getStringExtra("TARGET_HAND") ?: ManoObjetivo.ANY.name
            val frames = data.getIntExtra("FRAMES", 15)
            val extraType = data.getStringExtra("EXTRA_TYPE")
            
            val manoObjetivo = ManoObjetivo.valueOf(manoObjetivoStr)
            val newStep = PasoSecuencia(nombreGesto, manoObjetivo, frames)

            when (extraType) {
                "ACTIVATOR" -> {
                    comboActual.activador = newStep
                    updateHeaders()
                }

                "STEP" -> {
                    adapter.pasos.add(newStep)
                    adapter.notifyItemInserted(adapter.pasos.size - 1)
                }
                "EDIT_STEP" -> {
                    val editIndex = data.getIntExtra("EDIT_INDEX", -1)
                    if (editIndex != -1) {
                        adapter.pasos[editIndex] = newStep
                        adapter.notifyItemChanged(editIndex)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightNavigationBars = true
            isAppearanceLightStatusBars = false 
        }
        setContentView(R.layout.activity_sequence_config)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainSequenceConfig)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom + ime.bottom)
            
            val cardBack = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardBack)
            cardBack?.getChildAt(0)?.setPadding(0, systemBars.top, 0, 0)
            
            insets
        }

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
        rvSteps = findViewById(R.id.recyclerView)
        etComboName = findViewById(R.id.etComboName)
        tvActivatorDesc = findViewById(R.id.tvActivatorDesc)
        btnEditActivator = findViewById(R.id.btnEditActivator)
        tvAccionDesc = findViewById(R.id.tvAccionDesc)
        btnEditAccion = findViewById(R.id.btnEditAccion)
        btnSave = findViewById(R.id.btnSave)
        fabAdd = findViewById(R.id.fabAdd)

        if (!loadCurrentConfig()) {
            return
        }

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                adapter.moveItem(viewHolder.adapterPosition, target.adapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val deletedStep = adapter.pasos[position]
                
                adapter.removeItem(position)

                com.google.android.material.snackbar.Snackbar.make(
                    findViewById(R.id.mainSequenceConfig),
                    "Paso '${deletedStep.nombreGesto}' eliminado",
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                ).apply {
                    setAction("Deshacer") {
                        adapter.pasos.add(position, deletedStep)
                        adapter.notifyItemInserted(position)
                        adapter.notifyItemRangeChanged(0, adapter.pasos.size)
                    }
                    setActionTextColor(androidx.core.content.ContextCompat.getColor(this@SecuenciaConfigActivity, R.color.teal_primary))
                    view.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#073F4C"))
                    setTextColor(android.graphics.Color.WHITE)
                    show()
                }
            }

            override fun onChildDraw(
                c: android.graphics.Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val itemView = viewHolder.itemView
                    val paint = android.graphics.Paint().apply {
                        color = androidx.core.content.ContextCompat.getColor(this@SecuenciaConfigActivity, R.color.teal_primary)
                    }
                    val icon = androidx.core.content.ContextCompat.getDrawable(this@SecuenciaConfigActivity, android.R.drawable.ic_menu_delete)
                    val cornerRadius = 12 * resources.displayMetrics.density
                    
                    if (dX > 0) {
                        val rect = android.graphics.RectF(
                            itemView.left.toFloat(), itemView.top.toFloat(),
                            itemView.left + dX, itemView.bottom.toFloat()
                        )
                        c.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
                        icon?.let {
                            val iconMargin = (itemView.height - it.intrinsicHeight) / 2
                            val iconTop = itemView.top + iconMargin
                            val iconBottom = iconTop + it.intrinsicHeight
                            val iconLeft = itemView.left + iconMargin
                            val iconRight = iconLeft + it.intrinsicWidth
                            it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                            it.setTint(android.graphics.Color.WHITE)
                            it.draw(c)
                        }
                    } else if (dX < 0) {
                        val rect = android.graphics.RectF(
                            itemView.right + dX, itemView.top.toFloat(),
                            itemView.right.toFloat(), itemView.bottom.toFloat()
                        )
                        c.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
                        icon?.let {
                            val iconMargin = (itemView.height - it.intrinsicHeight) / 2
                            val iconTop = itemView.top + iconMargin
                            val iconBottom = iconTop + it.intrinsicHeight
                            val iconRight = itemView.right - iconMargin
                            val iconLeft = iconRight - it.intrinsicWidth
                            it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                            it.setTint(android.graphics.Color.WHITE)
                            it.draw(c)
                        }
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        })

        adapter = SecuenciaAdapter(comboActual.pasos, { holder ->
            itemTouchHelper.startDrag(holder)
        }, { position ->
            val intent = Intent(this, PasoWizardActivity::class.java)
            intent.putExtra("EXTRA_TYPE", "EDIT_STEP")
            intent.putExtra("EDIT_INDEX", position)
            
            // Pass the existing values to pre-select them in the wizard
            val step = comboActual.pasos[position]
            intent.putExtra("INITIAL_POSE", step.nombreGesto)
            intent.putExtra("INITIAL_HAND", step.manoObjetivo.name)
            intent.putExtra("INITIAL_FRAMES", step.cuadrosRequeridos)
            
            wizardLauncher.launch(intent)
        })
        rvSteps.layoutManager = LinearLayoutManager(this)
        rvSteps.adapter = adapter
        itemTouchHelper.attachToRecyclerView(rvSteps)

        etComboName.setText(comboActual.name)
        etComboName.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                comboActual.name = s?.toString()?.takeIf { it.isNotBlank() } ?: "Combo Sin Nombre"
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        fabAdd.setOnClickListener {
            launchWizard("STEP")
        }

        btnEditActivator.setOnClickListener {
            if (comboActual.activador != null) {
                comboActual.activador = null
                updateHeaders()
            } else {
                launchWizard("ACTIVATOR", null)
            }
        }
        
        findViewById<View>(R.id.cardActivator).setOnClickListener {
            if (comboActual.activador != null) {
                launchWizard("ACTIVATOR", comboActual.activador)
            } else {
                launchWizard("ACTIVATOR", null)
            }
        }

        btnEditAccion.setOnClickListener {
            showActionSelectionDialog()
        }
        
        findViewById<View>(R.id.cardAccionVinculada).setOnClickListener {
            showActionSelectionDialog()
        }

        btnSave.setOnClickListener {
            SecuenciaConfigManager.saveCombos(this, todosCombos)
            Toast.makeText(this, "Combo guardado", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun launchWizard(extraType: String, step: PasoSecuencia? = null) {
        val intent = Intent(this, PasoWizardActivity::class.java)
        intent.putExtra("EXTRA_TYPE", extraType)
        if (step != null) {
            intent.putExtra("INITIAL_POSE", step.nombreGesto)
            intent.putExtra("INITIAL_HAND", step.manoObjetivo.name)
            intent.putExtra("INITIAL_FRAMES", step.cuadrosRequeridos)
        }
        wizardLauncher.launch(intent)
    }

    private fun loadCurrentConfig(): Boolean {
        val comboId = intent.getStringExtra("COMBO_ID")
        todosCombos = SecuenciaConfigManager.loadCombos(this).toMutableList()
        
        comboIndex = todosCombos.indexOfFirst { it.id == comboId }
        if (comboIndex == -1) {
            Toast.makeText(this, "Error cargando combo", Toast.LENGTH_SHORT).show()
            finish()
            return false
        }
        
        comboActual = todosCombos[comboIndex]
        updateHeaders()
        return true
    }

    private fun updateHeaders() {
        if (comboActual.activador != null) {
            tvActivatorDesc.text = "${comboActual.activador?.nombreGesto} (${comboActual.activador?.manoObjetivo})"
            tvActivatorDesc.setTextColor(android.graphics.Color.parseColor("#073F4C"))
            btnEditActivator.text = "Quitar"
            btnEditActivator.setTextColor(android.graphics.Color.parseColor("#E53935"))
        } else {
            tvActivatorDesc.text = "Ninguno"
            tvActivatorDesc.setTextColor(android.graphics.Color.parseColor("#A0AAB5"))
            btnEditActivator.text = "Añadir"
            btnEditActivator.setTextColor(android.graphics.Color.parseColor("#3aafa9"))
        }
        
        if (comboActual.accionVinculada != null) {
            tvAccionDesc.text = comboActual.accionVinculada
            tvAccionDesc.setTextColor(android.graphics.Color.parseColor("#073F4C"))
            btnEditAccion.text = "Cambiar"
        } else {
            tvAccionDesc.text = "Ninguna"
            tvAccionDesc.setTextColor(android.graphics.Color.parseColor("#A0AAB5"))
            btnEditAccion.text = "Seleccionar"
        }
    }

    private fun showActionSelectionDialog() {
        val actions = arrayOf("Encender Luces Sala", "Apagar Luces", "Activar Alarma", "Desactivar Alarma", "Abrir Puerta", "Modo Nocturno", "Ninguna")
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Seleccionar Acción")
            .setItems(actions) { dialog, which ->
                if (which == actions.size - 1) {
                    comboActual.accionVinculada = null
                } else {
                    comboActual.accionVinculada = actions[which]
                }
                updateHeaders()
                dialog.dismiss()
            }
            .show()
    }
}
