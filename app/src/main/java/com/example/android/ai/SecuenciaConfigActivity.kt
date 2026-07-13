package com.example.android.ai
import com.example.android.R

import android.app.Activity
import android.content.Context
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.android.db.AppDatabase
import com.example.android.db.Gesto
import com.example.android.network.ApiHandler
import com.example.android.network.RetrofitClient
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class SecuenciaConfigActivity : AppCompatActivity() {

    private lateinit var tvComboNameDesc: TextView
    private lateinit var tvStepsDesc: TextView
    private lateinit var tvActivatorDesc: TextView
    private lateinit var tvVoiceCommandDesc: TextView
    private lateinit var tvAccionDesc: TextView
    private lateinit var btnSave: Button

    private lateinit var adapter: SecuenciaAdapter
    private lateinit var db: AppDatabase

    private var todosCombos: MutableList<Combo> = mutableListOf()
    private lateinit var comboActual: Combo
    private var comboIndex: Int = -1

    // Launcher existente para Pasos/Activadores
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
                    updateHeaders()
                }
                "EDIT_STEP" -> {
                    val editIndex = data.getIntExtra("EDIT_INDEX", -1)
                    if (editIndex != -1) {
                        adapter.pasos[editIndex] = newStep
                        adapter.notifyItemChanged(editIndex)
                        updateHeaders()
                    }
                }
            }
        }
    }

    // === NUEVO LAUNCHER PARA EL WIZARD DE VINCULAR DISPOSITIVO ===
    private val dispositivoWizardLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult

            val dispositivoId = data.getIntExtra("DISPOSITIVO_ID", -1)
            val nombreDispositivo = data.getStringExtra("DISPOSITIVO_NOMBRE") ?: "Dispositivo"
            val accionTipo = data.getIntExtra("ACCION_TIPO", -1) // 0 = Encender, 1 = Apagar, 2 = Alternar, -1 = Quitar/Ninguna

            if (dispositivoId == -1 || accionTipo == -1) {
                // Si el usuario seleccionó "Quitar acción" dentro de su Wizard
                comboActual.aparatoId = null
                comboActual.accionEncendido = null
                comboActual.accionVinculada = null
            } else {
                // Asignar los valores devueltos por el Wizard
                comboActual.aparatoId = dispositivoId
                comboActual.accionEncendido = when (accionTipo) {
                    0 -> true
                    1 -> false
                    else -> null // Alternar estado
                }
                val verbo = when (accionTipo) {
                    0 -> "Encender"
                    1 -> "Apagar"
                    else -> "Alternar"
                }
                comboActual.accionVinculada = "$verbo · $nombreDispositivo"
            }
            updateHeaders()
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

        db = AppDatabase.getDatabase(this)

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
        tvComboNameDesc = findViewById(R.id.tvComboNameDesc)
        tvStepsDesc = findViewById(R.id.tvStepsDesc)
        tvActivatorDesc = findViewById(R.id.tvActivatorDesc)
        tvVoiceCommandDesc = findViewById(R.id.tvVoiceCommandDesc)
        tvAccionDesc = findViewById(R.id.tvAccionDesc)
        btnSave = findViewById(R.id.btnSave)

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
                updateHeaders()

                com.google.android.material.snackbar.Snackbar.make(
                    findViewById(R.id.mainSequenceConfig),
                    "Paso '${deletedStep.nombreGesto}' eliminado",
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                ).apply {
                    setAction("Deshacer") {
                        adapter.pasos.add(position, deletedStep)
                        adapter.notifyItemInserted(position)
                        adapter.notifyItemRangeChanged(0, adapter.pasos.size)
                        updateHeaders()
                    }
                    setActionTextColor(androidx.core.content.ContextCompat.getColor(this@SecuenciaConfigActivity, R.color.teal_primary))
                    this.view.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#073F4C"))
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

            val step = comboActual.pasos[position]
            intent.putExtra("INITIAL_POSE", step.nombreGesto)
            intent.putExtra("INITIAL_HAND", step.manoObjetivo.name)
            intent.putExtra("INITIAL_FRAMES", step.cuadrosRequeridos)

            wizardLauncher.launch(intent)
            wizardLauncher.launch(intent)
        })

        findViewById<View>(R.id.cardComboName).setOnClickListener {
            val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_combo_name, null)
            val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
            bottomSheetDialog.setContentView(bottomSheetView)

            val etBottomSheetComboName = bottomSheetView.findViewById<EditText>(R.id.etBottomSheetComboName)
            val btnSaveComboName = bottomSheetView.findViewById<Button>(R.id.btnSaveComboName)

            etBottomSheetComboName.setText(comboActual.name)

            btnSaveComboName.setOnClickListener {
                val name = etBottomSheetComboName.text.toString().trim()
                comboActual.name = name.takeIf { it.isNotBlank() } ?: "Combo Sin Nombre"
                updateHeaders()
                bottomSheetDialog.dismiss()
            }

            bottomSheetDialog.setOnShowListener {
                etBottomSheetComboName.requestFocus()
                etBottomSheetComboName.post {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(etBottomSheetComboName, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
            }
            bottomSheetDialog.show()
        }

        findViewById<View>(R.id.cardStepsSummary).setOnClickListener {
            val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_steps, null)
            val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
            bottomSheetDialog.setContentView(bottomSheetView)

            val rvBottomSheetSteps = bottomSheetView.findViewById<RecyclerView>(R.id.rvStepsBottomSheet)
            rvBottomSheetSteps.layoutManager = LinearLayoutManager(this)
            rvBottomSheetSteps.adapter = adapter
            itemTouchHelper.attachToRecyclerView(rvBottomSheetSteps)

            bottomSheetView.findViewById<View>(R.id.fabAddStep).setOnClickListener {
                launchWizard("STEP")
            }

            bottomSheetDialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            bottomSheetDialog.show()
        }

        val ivComboIcon = findViewById<android.widget.ImageView>(R.id.ivComboIcon)

        val availableIcons = listOf(
            "lucide_lightbulb", "lucide_tv", "lucide_music", "lucide_fan", "lucide_zap",
            "lucide_home", "lucide_door_open", "lucide_door_closed", "lucide_lock", "lucide_unlock",
            "lucide_thermometer", "lucide_snowflake", "lucide_sun", "lucide_moon", "lucide_coffee",
            "lucide_gamepad_2", "lucide_wifi", "lucide_camera", "lucide_video", "lucide_shield",
            "lucide_bell", "lucide_heart", "lucide_star", "lucide_settings", "lucide_play",
            "lucide_pause", "lucide_volume_2", "lucide_volume_x", "lucide_wand_2", "lucide_power"
        )

        findViewById<View>(R.id.cardIcon).setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_icon_picker, null)
            val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
            dialog.setContentView(dialogView)
            dialog.show()

            val rvIcons = dialogView.findViewById<RecyclerView>(R.id.rvIcons)
            val etSearchIcon = dialogView.findViewById<EditText>(R.id.etSearchIcon)

            rvIcons.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 5)
            val iconAdapter = com.example.android.ui.IconPickerAdapter(availableIcons) { selectedIcon ->
                comboActual.icono = selectedIcon
                updateHeaders()
                dialog.dismiss()
            }
            rvIcons.adapter = iconAdapter

            etSearchIcon.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    iconAdapter.filter(s?.toString() ?: "")
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }

        findViewById<View>(R.id.cardVoiceCommand).setOnClickListener {
            val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_voice_command, null)
            val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
            bottomSheetDialog.setContentView(bottomSheetView)

            val etBottomSheetVoice = bottomSheetView.findViewById<EditText>(R.id.etBottomSheetVoiceCommand)
            val btnSaveVoice = bottomSheetView.findViewById<Button>(R.id.btnSaveVoiceCommand)

            etBottomSheetVoice.setText(comboActual.fraseVozActivadora)

            btnSaveVoice.setOnClickListener {
                val command = etBottomSheetVoice.text.toString().trim()
                comboActual.fraseVozActivadora = command.takeIf { it.isNotBlank() }
                updateHeaders()
                bottomSheetDialog.dismiss()
            }

            bottomSheetDialog.setOnShowListener {
                etBottomSheetVoice.requestFocus()
                etBottomSheetVoice.post {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(etBottomSheetVoice, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
            }

            bottomSheetDialog.show()
        }

        findViewById<View>(R.id.cardActivator).setOnClickListener {
            if (comboActual.activador != null) {
                // If it already has an activator, maybe we want to edit or delete it
                // For simplicity, we just launch the wizard to replace it.
                // You could also show a dialog to let the user delete it, but wizard is fine.
                launchWizard("ACTIVATOR", comboActual.activador)
            } else {
                launchWizard("ACTIVATOR", null)
            }
        }

        // Eventos modificados para abrir la nueva actividad Wizard en lugar del diálogo antiguo
        findViewById<View>(R.id.cardAccionVinculada).setOnClickListener {
            showActionSelectionDialog()
        }

        btnSave.setOnClickListener {
            SecuenciaConfigManager.saveCombos(this, todosCombos)
            notificarRecargaCombos()
            sincronizarGestoConServidor()
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
        tvComboNameDesc.text = comboActual.name.ifBlank { "Sin Nombre" }
        tvStepsDesc.text = "${comboActual.pasos.size} paso(s) configurado(s)"

        val ivComboIcon = findViewById<android.widget.ImageView>(R.id.ivComboIcon)
        if (ivComboIcon != null) {
            val iconName = comboActual.icono ?: "lucide_star"
            val resId = resources.getIdentifier(iconName, "drawable", packageName)
            if (resId != 0) {
                ivComboIcon.setImageResource(resId)
            } else {
                ivComboIcon.setImageResource(R.drawable.lucide_star)
            }
        }

        if (comboActual.activador != null) {
            tvActivatorDesc.text = "${comboActual.activador?.nombreGesto} (${comboActual.activador?.manoObjetivo})"
            tvActivatorDesc.setTextColor(android.graphics.Color.parseColor("#073F4C"))
        } else {
            tvActivatorDesc.text = "Ninguno"
            tvActivatorDesc.setTextColor(android.graphics.Color.parseColor("#A0AAB5"))
        }

        if (!comboActual.fraseVozActivadora.isNullOrBlank()) {
            tvVoiceCommandDesc.text = comboActual.fraseVozActivadora
            tvVoiceCommandDesc.setTextColor(android.graphics.Color.parseColor("#073F4C"))
        } else {
            tvVoiceCommandDesc.text = "Ninguno"
            tvVoiceCommandDesc.setTextColor(android.graphics.Color.parseColor("#A0AAB5"))
        }

        if (comboActual.accionVinculada != null) {
            tvAccionDesc.text = comboActual.accionVinculada
            tvAccionDesc.setTextColor(android.graphics.Color.parseColor("#073F4C"))
        } else {
            tvAccionDesc.text = "Ninguna"
            tvAccionDesc.setTextColor(android.graphics.Color.parseColor("#A0AAB5"))
        }
    }

    // === FUNCIÓN REDISEÑADA: ABRE LA NUEVA ACTIVIDAD DE TIPO WIZARD ===
    private fun showActionSelectionDialog() {
        // Debes crear una nueva Activity llamada 'DispositivoWizardActivity' (o el nombre que prefieras)
        val intent = Intent(this, DispositivoWizardActivity::class.java).apply {
            putExtra("INITIAL_DISPOSITIVO_ID", comboActual.aparatoId ?: -1)
            putExtra("INITIAL_ACCION_ENCENDIDO", comboActual.accionEncendido)
        }
        dispositivoWizardLauncher.launch(intent)
    }

    private fun notificarRecargaCombos() {
        val intent = Intent(this, BackgroundCameraService::class.java).apply {
            action = BackgroundCameraService.ACTION_RELOAD_COMBOS
        }
        startService(intent)
    }

    // ==========================================================
    // SINCRONIZACIÓN CON BACKEND
    // ==========================================================

    private fun sincronizarGestoConServidor() {
        val nombreRepresentativo = comboActual.activador?.nombreGesto
            ?: comboActual.pasos.firstOrNull()?.nombreGesto
            ?: comboActual.name
        val nombreValido = comboActual.name.ifBlank { nombreRepresentativo }
        val tipoDisparador = if (comboActual.activador != null) "COMBO_SECUENCIA" else "COMBO_LIBRE"

        val pasosList = mutableListOf<com.example.android.db.GestoPaso>()
        var order = 1
        if (comboActual.activador != null) {
            pasosList.add(com.example.android.db.GestoPaso(orden = order++, esActivador = true, nombreGesto = comboActual.activador!!.nombreGesto, manoObjetivo = comboActual.activador!!.manoObjetivo.name, cuadrosRequeridos = comboActual.activador!!.cuadrosRequeridos))
        }
        comboActual.pasos.forEach { paso ->
            pasosList.add(com.example.android.db.GestoPaso(orden = order++, esActivador = false, nombreGesto = paso.nombreGesto, manoObjetivo = paso.manoObjetivo.name, cuadrosRequeridos = paso.cuadrosRequeridos))
        }

        val gesto = Gesto(
            id = comboActual.backendGestoId ?: 0,
            bkId = comboActual.backendGestoId ?: 0,
            nombre = nombreValido,
            identificadorIa = 0,
            nivelConfianzaMinimo = 0.5,
            tipoDisparadorNombre = tipoDisparador,
            aparatoId = comboActual.aparatoId,
            pasos = pasosList,
            fraseVozActivadora = comboActual.fraseVozActivadora
        )
        lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = this@SecuenciaConfigActivity,
                showLoading = true,
                loadingTitle = "Guardando",
                loadingMessage = "Sincronizando gesto...",
                apiCall = {
                    val token = getSharedPreferences("SesionApp", Context.MODE_PRIVATE).getString("apiToken", "") ?: ""
                    val bearer = "Bearer $token"
                    if (gesto.id == 0) {
                        RetrofitClient.gestureService.createGesto(bearer, gesto)
                    } else {
                        val updateResp = RetrofitClient.gestureService.updateGesto(bearer, gesto.id, gesto)
                        if (updateResp.isSuccessful) {
                            retrofit2.Response.success(com.example.android.network.ApiResponse(true, 200, gesto))
                        } else {
                            retrofit2.Response.error(updateResp.code(), updateResp.errorBody()!!)
                        }
                    }
                },
                onSuccess = { response ->
                    val guardado = response.data
                    if (guardado != null) {
                        comboActual.backendGestoId = guardado.id
                        todosCombos[comboIndex] = comboActual
                        SecuenciaConfigManager.saveCombos(this@SecuenciaConfigActivity, todosCombos)
                        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            db.gestoDao().insertGesto(guardado)
                        }
                    }
                    Toast.makeText(this@SecuenciaConfigActivity, "Gesto guardado y sincronizado", Toast.LENGTH_SHORT).show()
                    finish()
                },
                onError = { errorMsg ->
                    Toast.makeText(
                        this@SecuenciaConfigActivity,
                        "Gesto guardado localmente, pero falló la sincronización: $errorMsg",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            )
        }
    }
}