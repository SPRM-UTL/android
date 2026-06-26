package com.example.android.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.android.R
import com.example.android.databinding.ActivityLugaresBinding
import com.example.android.db.AppDatabase
import com.example.android.db.Casa
import com.example.android.db.Habitacion
import com.example.android.network.ApiHandler
import com.example.android.network.RetrofitClient
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LugaresActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLugaresBinding
    private lateinit var db: AppDatabase
    private lateinit var habitacionAdapter: HabitacionesEditAdapter
    private var currentCasaId: Int? = null
    private var habitacionesJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityLugaresBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        db = AppDatabase.getDatabase(this)

        binding.btnBack.setOnClickListener { finish() }

        habitacionAdapter = HabitacionesEditAdapter(
            onEditClick = { habitacion -> mostrarDialogoEditarHabitacion(habitacion) },
            onDeleteClick = { habitacion -> mostrarDialogoEliminarHabitacion(habitacion) }
        )
        binding.rvHabitaciones.layoutManager = LinearLayoutManager(this)
        binding.rvHabitaciones.adapter = habitacionAdapter

        binding.btnAddCasa.setOnClickListener { mostrarDialogoAgregarCasa() }
        binding.btnEditCasa.setOnClickListener { currentCasaId?.let { mostrarDialogoEditarCasa(it) } }
        binding.btnDeleteCasa.setOnClickListener { currentCasaId?.let { mostrarDialogoEliminarCasa(it) } }
        binding.btnAddHabitacion.setOnClickListener { currentCasaId?.let { mostrarDialogoAgregarHabitacion(it) } }

        cargarCasas()
    }

    private fun cargarCasas() {
        lifecycleScope.launch {
            db.casaDao().getAllCasas().collectLatest { casas ->
                binding.tabLayoutCasas.removeAllTabs()
                
                if (casas.isEmpty()) {
                    binding.layoutCasaActions.visibility = View.GONE
                    binding.tvHabitacionesTitle.visibility = View.GONE
                    binding.btnAddHabitacion.visibility = View.GONE
                    binding.rvHabitaciones.visibility = View.GONE
                    habitacionAdapter.submitList(emptyList())
                    return@collectLatest
                }

                binding.layoutCasaActions.visibility = View.VISIBLE
                binding.tvHabitacionesTitle.visibility = View.VISIBLE
                binding.btnAddHabitacion.visibility = View.VISIBLE
                binding.rvHabitaciones.visibility = View.VISIBLE

                binding.tabLayoutCasas.clearOnTabSelectedListeners()
                casas.forEach { casa ->
                    val tab = binding.tabLayoutCasas.newTab().setText(casa.nombre)
                    tab.tag = casa.id
                    binding.tabLayoutCasas.addTab(tab)
                    if (currentCasaId == casa.id) {
                        binding.tabLayoutCasas.selectTab(tab)
                    }
                }

                binding.tabLayoutCasas.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(tab: TabLayout.Tab?) {
                        currentCasaId = tab?.tag as? Int
                        observarHabitaciones()
                    }
                    override fun onTabUnselected(tab: TabLayout.Tab?) {}
                    override fun onTabReselected(tab: TabLayout.Tab?) {
                        currentCasaId = tab?.tag as? Int
                        observarHabitaciones()
                    }
                })

                if (currentCasaId == null && casas.isNotEmpty()) {
                    currentCasaId = casas[0].id
                    binding.tabLayoutCasas.getTabAt(0)?.select()
                }
                
                observarHabitaciones()
            }
        }
    }

    private fun observarHabitaciones() {
        habitacionesJob?.cancel()
        habitacionesJob = lifecycleScope.launch {
            currentCasaId?.let { casaId ->
                db.habitacionDao().getHabitacionesByCasa(casaId).collectLatest { habitaciones ->
                    habitacionAdapter.submitList(habitaciones)
                }
            }
        }
    }

    private fun getToken(): String {
        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        return sharedPref.getString("apiToken", "") ?: ""
    }

    private fun getUserId(): Int {
        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        return sharedPref.getInt("userId", 0)
    }

    private fun mostrarDialogoAgregarCasa() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_casa, null)
        val dialog = MaterialAlertDialogBuilder(this, com.google.android.material.R.style.Theme_Material3_Light_Dialog)
            .setView(dialogView)
            .show()

        val etNombreCasa = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNombreCasa)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnConfirm = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirm)
        btnConfirm.text = "Crear"

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            val nombre = etNombreCasa.text?.toString()?.trim() ?: ""
            if (nombre.isNotEmpty()) {
                crearNuevaCasa(nombre)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Ingresa un nombre válido", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun crearNuevaCasa(nombre: String) {
        lifecycleScope.launch {
            val nuevaCasa = Casa(id = 0, nombre = nombre, skUsuarioId = getUserId())
            ApiHandler.safeApiCall(
                activity = this@LugaresActivity,
                showLoading = true,
                loadingTitle = "Creando...",
                apiCall = { RetrofitClient.casaService.createCasa("Bearer ${getToken()}", nuevaCasa) },
                onSuccess = { response ->
                    // Navegar a la nueva casa tras la sincronización
                    val nuevaId = response.data?.id
                    if (nuevaId != null && nuevaId > 0) currentCasaId = nuevaId
                    sincronizarCasasYHabitaciones()
                }
            )
        }
    }

    private fun mostrarDialogoEditarCasa(casaId: Int) {
        lifecycleScope.launch {
            val casa = withContext(Dispatchers.IO) { db.casaDao().getCasaById(casaId) } ?: return@launch

            val dialogView = LayoutInflater.from(this@LugaresActivity).inflate(R.layout.dialog_add_casa, null)
            val dialog = MaterialAlertDialogBuilder(this@LugaresActivity, com.google.android.material.R.style.Theme_Material3_Light_Dialog)
                .setView(dialogView)
                .show()

            val title = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
            title.text = "Editar Casa"

            val etNombreCasa = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNombreCasa)
            etNombreCasa.setText(casa.nombre)

            val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
            val btnConfirm = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirm)
            btnConfirm.text = "Modificar"

            btnCancel.setOnClickListener { dialog.dismiss() }
            btnConfirm.setOnClickListener {
                val nombre = etNombreCasa.text?.toString()?.trim() ?: ""
                if (nombre.isNotEmpty()) {
                    editarCasa(casa.copy(nombre = nombre))
                    dialog.dismiss()
                } else {
                    Toast.makeText(this@LugaresActivity, "Ingresa un nombre válido", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun editarCasa(casa: Casa) {
        lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = this@LugaresActivity,
                showLoading = true,
                loadingTitle = "Actualizando...",
                apiCall = { RetrofitClient.casaService.updateCasa("Bearer ${getToken()}", casa.id, casa) },
                onSuccess = { _ ->
                    withContext(Dispatchers.IO) { db.casaDao().updateCasa(casa) }
                }
            )
        }
    }

    private fun mostrarDialogoEliminarCasa(casaId: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Eliminar Casa")
            .setMessage("¿Estás seguro de que deseas eliminar esta casa? Todos los dispositivos en esta casa perderán su ubicación.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                eliminarCasa(casaId)
            }
            .show()
    }

    private fun eliminarCasa(casaId: Int) {
        lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = this@LugaresActivity,
                showLoading = true,
                loadingTitle = "Eliminando...",
                apiCall = { RetrofitClient.casaService.deleteCasa("Bearer ${getToken()}", casaId) },
                onSuccess = { _ ->
                    currentCasaId = null
                    sincronizarCasasYHabitaciones()
                }
            )
        }
    }

    private fun mostrarDialogoAgregarHabitacion(casaId: Int) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_habitacion, null)
        val dialog = MaterialAlertDialogBuilder(this, com.google.android.material.R.style.Theme_Material3_Light_Dialog)
            .setView(dialogView)
            .show()

        val etNombreHabitacion = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNombreHabitacion)
        
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnConfirm = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirm)
        btnConfirm.text = "Crear"
        
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            val nombre = etNombreHabitacion.text?.toString()?.trim() ?: ""
            if (nombre.isNotEmpty()) {
                crearNuevaHabitacion(nombre, casaId)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Ingresa un nombre válido", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun crearNuevaHabitacion(nombre: String, casaId: Int) {
        lifecycleScope.launch {
            val nuevaHab = Habitacion(id = 0, nombre = nombre, skCasaId = casaId)
            ApiHandler.safeApiCall(
                activity = this@LugaresActivity,
                showLoading = true,
                loadingTitle = "Creando...",
                apiCall = { RetrofitClient.habitacionService.createHabitacion("Bearer ${getToken()}", nuevaHab) },
                onSuccess = { _ -> sincronizarCasasYHabitaciones() }
            )
        }
    }

    private fun mostrarDialogoEditarHabitacion(habitacion: Habitacion) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_habitacion, null)
        val dialog = MaterialAlertDialogBuilder(this, com.google.android.material.R.style.Theme_Material3_Light_Dialog)
            .setView(dialogView)
            .show()

        val title = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        title.text = "Editar Habitación"

        val etNombreHabitacion = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNombreHabitacion)
        etNombreHabitacion.setText(habitacion.nombre)

        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnConfirm = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirm)
        btnConfirm.text = "Modificar"

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            val nombre = etNombreHabitacion.text?.toString()?.trim() ?: ""
            if (nombre.isNotEmpty()) {
                editarHabitacion(habitacion.copy(nombre = nombre))
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Ingresa un nombre válido", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun editarHabitacion(habitacion: Habitacion) {
        lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = this@LugaresActivity,
                showLoading = true,
                loadingTitle = "Actualizando...",
                apiCall = { RetrofitClient.habitacionService.updateHabitacion("Bearer ${getToken()}", habitacion.id, habitacion) },
                onSuccess = { _ ->
                    withContext(Dispatchers.IO) { db.habitacionDao().updateHabitacion(habitacion) }
                }
            )
        }
    }

    private fun mostrarDialogoEliminarHabitacion(habitacion: Habitacion) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Eliminar Habitación")
            .setMessage("¿Estás seguro de que deseas eliminar esta habitación?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                eliminarHabitacion(habitacion.id)
            }
            .show()
    }

    private fun eliminarHabitacion(habitacionId: Int) {
        lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = this@LugaresActivity,
                showLoading = true,
                loadingTitle = "Eliminando...",
                apiCall = { RetrofitClient.habitacionService.deleteHabitacion("Bearer ${getToken()}", habitacionId) },
                onSuccess = { _ ->
                    withContext(Dispatchers.IO) {
                        val hab = db.habitacionDao().getHabitacionById(habitacionId)
                        if(hab != null) db.habitacionDao().delete(hab)
                    }
                }
            )
        }
    }

    private fun sincronizarCasasYHabitaciones() {
        lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = this@LugaresActivity,
                showLoading = false,
                loadingTitle = "Sincronizando...",
                apiCall = { RetrofitClient.casaService.getCasas("Bearer ${getToken()}") },
                onSuccess = { response ->
                    withContext(Dispatchers.IO) {
                        db.casaDao().deleteAllCasas()
                        if (response.data != null) db.casaDao().insertAll(response.data)
                        db.habitacionDao().deleteAllHabitaciones()
                        
                        response.data?.forEach { casa ->
                            try {
                                val habRes = RetrofitClient.habitacionService.getHabitacionesByCasa("Bearer ${getToken()}", casa.id)
                                if (habRes.isSuccessful) {
                                    habRes.body()?.data?.let { db.habitacionDao().insertAll(it) }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            )
        }
    }
}
