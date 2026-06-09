package com.example.android

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.android.db.AppDatabase
import com.example.android.db.Dispositivo
import com.example.android.db.Gesto
import com.example.android.network.RetrofitClient
import com.example.android.ui.GestureAdapter
import com.example.android.ui.components.BottomBarWithFab
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import android.content.Context
import android.view.View
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.android.view.Snackbars
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial

class Gestos : AppCompatActivity() {

    private lateinit var gestureAdapter: GestureAdapter
    private lateinit var db: AppDatabase
    private var dispositivosLocales: List<Dispositivo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = true
        
        setContentView(R.layout.activity_gestos)

        db = AppDatabase.getDatabase(this)

        val mainGestos = findViewById<androidx.constraintlayout.motion.widget.MotionLayout>(R.id.mainGestos)
        ViewCompat.setOnApplyWindowInsetsListener(mainGestos) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            
            val bottomContainer = findViewById<View>(R.id.bottom_bar_container)
            bottomContainer?.setPadding(0, 0, 0, systemBars.bottom)
            
            // Asegurar que el FAB suba con la barra si hay DPI bajo/teclas de navegacion
            val fab = findViewById<View>(R.id.fabAddGesture)
            val params = fab.layoutParams as android.view.ViewGroup.MarginLayoutParams
            params.bottomMargin = (16 * resources.displayMetrics.density).toInt() 
            fab.layoutParams = params

            insets
        }

        mainGestos.post {
            mainGestos.transitionToEnd()
        }

        configurarBottomBarCompose()
        setupRecyclerView()

        findViewById<FloatingActionButton>(R.id.fabAddGesture).setOnClickListener {
            showGestureDialog(null)
        }

        lifecycleScope.launch {
            db.dispositivoDao().getAllDispositivos().collectLatest { dispositivos ->
                dispositivosLocales = dispositivos
                gestureAdapter.notifyDataSetChanged()
            }
        }

        lifecycleScope.launch {
            db.gestoDao().getAllGestos().collectLatest { gestos ->
                gestureAdapter.submitList(gestos)
            }
        }

        syncGestosFromApi()
    }

    private fun setupRecyclerView() {
        val rvGestos = findViewById<RecyclerView>(R.id.rvGestos)
        rvGestos.layoutManager = GridLayoutManager(this, 2)
        gestureAdapter = GestureAdapter(
            getDeviceName = { deviceId ->
                if (deviceId == null) "Sin asignar"
                else dispositivosLocales.find { it.id == deviceId }?.nombre ?: "Desconocido"
            },
            onEditClick = { showGestureDialog(it) },
            onDeleteClick = { deleteGesto(it) },
            onToggleClick = { gesto, isChecked ->
                Toast.makeText(this, "${gesto.nombre} -> $isChecked", Toast.LENGTH_SHORT).show()
            }
        )
        rvGestos.adapter = gestureAdapter
    }

    private fun syncGestosFromApi() {
        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val token = sharedPref.getString("apiToken", "") ?: ""
        val bearer = "Bearer $token"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.gestureService.getGestos(bearer)
                if (response.isSuccessful) {
                    val apiGestos = response.body()?.data ?: emptyList()
                    db.gestoDao().deleteAllGestos()
                    db.gestoDao().insertAll(apiGestos)
                }
            } catch (e: Exception) {
                Log.e("Gestos", "Error syncing gestos", e)
            }
        }
    }

    private fun showGestureDialog(gestoExistente: Gesto?) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setContentView(R.layout.dialog_gesture_form)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val etName = dialog.findViewById<MaterialAutoCompleteTextView>(R.id.etGestureName)
        val spinnerDevices = dialog.findViewById<Spinner>(R.id.spinnerDevices)

        val deviceNames = dispositivosLocales.map { it.nombre ?: "Desconocido" }.toMutableList()
        deviceNames.add(0, "Ninguno (Sin asignar)")
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, deviceNames)
        spinnerDevices.adapter = adapter

        val gestosValidos = listOf("Manos Arriba", "Una Mano Arriba", "Agitar la Mano", "Abrir Puño", "Cerrar Puño")
        val gestoAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, gestosValidos)
        etName.setAdapter(gestoAdapter)

        if (gestoExistente != null) {
            etName.setText(gestoExistente.nombre, false)
            
            val deviceIndex = dispositivosLocales.indexOfFirst { it.id == gestoExistente.aparatoId }
            if (deviceIndex != -1) {
                spinnerDevices.setSelection(deviceIndex + 1)
            }
        }

        dialog.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.findViewById<Button>(R.id.btnSave).setOnClickListener {
            val name = etName.text.toString()

            if (name.isNotBlank()) {
                val selectedPos = spinnerDevices.selectedItemPosition
                val assignedDeviceId = if (selectedPos > 0) dispositivosLocales[selectedPos - 1].id else null

                // Asignación automática de confianza según el gesto
                val confianzaAutomatica = when (name) {
                    "Manos Arriba" -> 85.0
                    "Cerrar Puño" -> 85.0
                    "Una Mano Arriba" -> 80.0
                    "Abrir Puño" -> 80.0
                    "Agitar la Mano" -> 70.0
                    else -> 80.0
                }

                val nuevoGesto = Gesto(
                    id = gestoExistente?.id ?: 0,
                    bkId = gestoExistente?.bkId ?: 0,
                    nombre = name,
                    identificadorIa = gestoExistente?.identificadorIa ?: 1,
                    nivelConfianzaMinimo = confianzaAutomatica,
                    tipoDisparadorNombre = "Continuo",
                    aparatoId = assignedDeviceId
                )
                saveGesto(nuevoGesto, isUpdate = gestoExistente != null)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Debe seleccionar un gesto", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun saveGesto(gesto: Gesto, isUpdate: Boolean) {
        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val token = sharedPref.getString("apiToken", "") ?: ""
        val bearer = "Bearer $token"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = if (isUpdate) {
                    RetrofitClient.gestureService.updateGesto(bearer, gesto.id, gesto)
                    retrofit2.Response.success(com.example.android.network.ApiResponse(true, 200, gesto))
                } else {
                    RetrofitClient.gestureService.createGesto(bearer, gesto)
                }

                if (response.isSuccessful) {
                    val savedGesto = response.body()?.data
                    if (savedGesto != null) {
                        db.gestoDao().insertGesto(savedGesto)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@Gestos, "Guardado exitoso", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@Gestos, "Error al guardar gesto", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("Gestos", "Error saving", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@Gestos, "Error de red", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteGesto(gesto: Gesto) {
        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val token = sharedPref.getString("apiToken", "") ?: ""
        val bearer = "Bearer $token"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.gestureService.deleteGesto(bearer, gesto.id)
                if (response.isSuccessful) {
                    db.gestoDao().deleteGesto(gesto)
                }
            } catch (e: Exception) {
                Log.e("Gestos", "Error deleting", e)
            }
        }
    }

    private fun configurarBottomBarCompose() {
        val composeContainer = findViewById<FrameLayout>(R.id.bottom_bar_container)
        val composeView = ComposeView(this).apply {
            setContent {
                var isMenuOpen by remember { mutableStateOf(false) }
                BottomBarWithFab(
                    currentScreen = "gestos",
                    isMenuOpen = isMenuOpen,
                    onHomeClick = {
                        val intent = Intent(this@Gestos, HomeActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        startActivity(intent)
                        overridePendingTransition(0, 0)
                        finish()
                    },
                    onGesturesClick = {},
                    onFabClick = {
                        isMenuOpen = true
                        abrirMenuPrincipal(onDismiss = { isMenuOpen = false })
                    }
                )
            }
        }
        composeContainer.addView(composeView)
    }

    private fun abrirMenuPrincipal(onDismiss: () -> Unit = {}) {
        if (supportFragmentManager.findFragmentByTag("MenuBottomSheet") != null) return
        val sheet = MenuBottomSheetDialog(this)
        sheet.onDismissCallback = onDismiss
        sheet.show(supportFragmentManager, "MenuBottomSheet")
    }
}