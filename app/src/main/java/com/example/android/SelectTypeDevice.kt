package com.example.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import android.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.android.network.ApiHandler
import com.example.android.network.RetrofitClient
import kotlinx.coroutines.launch
import android.text.Editable
import android.text.TextWatcher
import com.google.android.material.textfield.TextInputEditText

class SelectTypeDevice : AppCompatActivity() {

    private lateinit var adapter: AparatoTipoAdapter
    private lateinit var recyclerView: RecyclerView

    private var allTipos: List<com.example.android.db.AparatoTipo> = emptyList()
    private var isExpanded = false
    private var filterState = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.WHITE
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = true
        }

        setContentView(R.layout.activity_select_type_device)

        val rootView = findViewById<View>(android.R.id.content)
        val header = findViewById<View>(R.id.header)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            
            header.setPadding(
                header.paddingLeft,
                systemBars.top,
                header.paddingRight,
                (12 * resources.displayMetrics.density).toInt()
            )

            recyclerView.setPadding(
                recyclerView.paddingLeft,
                recyclerView.paddingTop,
                recyclerView.paddingRight,
                systemBars.bottom + (16 * resources.displayMetrics.density).toInt()
            )
            insets
        }

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        this.recyclerView = recyclerView

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AparatoTipoAdapter(emptyList()) { tipo ->
            if (tipo.soportaBluetooth || tipo.soportaWifi) {
                mostrarBottomSheetEleccion(tipo)
            } else {
                crearDispositivoGenerico(tipo)
            }
        }
        recyclerView.adapter = adapter

        val btnVerMas = findViewById<View>(R.id.btnVerMas)
        btnVerMas.setOnClickListener {
            isExpanded = true
            actualizarLista()
        }

        val etSearch = findViewById<TextInputEditText>(R.id.etSearch)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                actualizarLista(s?.toString() ?: "")
            }
        })

        val searchLayout = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.searchLayout)
        searchLayout.setEndIconOnClickListener {
            filterState = (filterState + 1) % 3
            when (filterState) {
                0 -> searchLayout.endIconDrawable = getDrawable(R.drawable.ic_filter_all)
                1 -> searchLayout.endIconDrawable = getDrawable(R.drawable.wifi)
                2 -> searchLayout.endIconDrawable = getDrawable(R.drawable.bluetooth)
            }
            actualizarLista(etSearch.text?.toString() ?: "")
        }

        cargarTiposDispositivos()
    }

    private fun crearDispositivoGenerico(tipo: com.example.android.db.AparatoTipo) {
        val sharedPref = getSharedPreferences("EspConfigPrefs", Context.MODE_PRIVATE)
        val espMac = sharedPref.getString("saved_mac_address", "") ?: ""

        if (espMac.isBlank()) {
            Toast.makeText(this, "Primero configura tu Controlador desde 'Estado de la red'", Toast.LENGTH_LONG).show()
            return
        }

        val input = com.google.android.material.textfield.TextInputEditText(this)
        input.hint = "Ej. ${tipo.nombreTipo} Sala"

        val layout = android.widget.FrameLayout(this)
        val padding = (20 * resources.displayMetrics.density).toInt()
        layout.setPadding(padding, padding, padding, padding)
        layout.addView(input)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Nombrar ${tipo.nombreTipo}")
            .setMessage("¿Qué nombre le vas a poner a tu ${tipo.nombreTipo}?")
            .setView(layout)
            .setCancelable(true)
            .setPositiveButton("Guardar") { dialog, _ ->
                val nombre = input.text.toString().trim()
                if (nombre.isNotEmpty()) {
                    guardarDispositivoNuevo(nombre, tipo, espMac)
                } else {
                    Toast.makeText(this, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun guardarDispositivoNuevo(nombre: String, tipo: com.example.android.db.AparatoTipo, espMac: String) {
        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val token = sharedPref.getString("apiToken", "") ?: return

        val dispositivo = com.example.android.db.Dispositivo(
            id = 0,
            nombre = nombre,
            tipo = tipo.nombreTipo,
            accion = null,
            comandoBluetooth = null,
            icono = tipo.icono,
            macBluetooth = espMac,
            nombreBluetooth = null,
            fechaSincronizacion = null
        )

        lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = this@SelectTypeDevice,
                showLoading = true,
                loadingTitle = "Guardando",
                loadingMessage = "Registrando dispositivo en tu cuenta...",
                apiCall = { RetrofitClient.deviceService.createDispositivo("Bearer $token", dispositivo) },
                onSuccess = { response ->
                    val nuevoDispositivo = response.data
                    if (nuevoDispositivo != null) {
                        guardarConfiguracionRed(nuevoDispositivo.id, espMac)
                    } else {
                        Toast.makeText(this@SelectTypeDevice, "Error: El servidor no devolvió el dispositivo creado", Toast.LENGTH_SHORT).show()
                    }
                },
                onError = { error ->
                    Toast.makeText(this@SelectTypeDevice, "Error al crear dispositivo: $error", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun guardarConfiguracionRed(aparatoId: Int, espMac: String) {
        lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = this@SelectTypeDevice,
                showLoading = true,
                loadingTitle = "Guardando",
                loadingMessage = "Guardando configuración de red...",
                apiCall = {
                    val token = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
                        .getString("apiToken", "") ?: ""
                    val config = com.example.android.network.ConfiguracionRedRequest(
                        ipAddress = "0.0.0.0", // La IP real se maneja dinámicamente por el Backend WebSocket
                        macAddress = espMac,
                        hostName = null,
                        deviceKey = espMac,
                        puertoSocket = 81,
                        protocoloSocket = "ws",
                        rutaSocket = "/ws",
                        activo = true
                    )
                    RetrofitClient.deviceService.saveConfiguracionRed("Bearer $token", aparatoId, config)
                },
                onSuccess = {
                    Toast.makeText(this@SelectTypeDevice, "Dispositivo guardado correctamente", Toast.LENGTH_SHORT).show()
                    finish()
                },
                onError = { errorMsg ->
                    Toast.makeText(this@SelectTypeDevice, "Error al guardar red: $errorMsg", Toast.LENGTH_SHORT).show()
                    finish()
                }
            )
        }
    }

    private fun cargarTiposDispositivos() {
        val sharedPreferences = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("apiToken", null)

        if (token.isNullOrEmpty()) {
            Toast.makeText(this, "Error de autenticación", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = this@SelectTypeDevice,
                showLoading = true,
                loadingTitle = "Buscando",
                loadingMessage = "Cargando dispositivos...",
                apiCall = { RetrofitClient.deviceService.getTiposAparato("Bearer $token") },
                onSuccess = { apiResponse ->
                    if (apiResponse.success && apiResponse.data != null) {
                        allTipos = apiResponse.data
                        actualizarLista()
                    } else {
                        Toast.makeText(this@SelectTypeDevice, "Error al obtener tipos", Toast.LENGTH_SHORT).show()
                    }
                },
                onError = { error ->
                    Toast.makeText(this@SelectTypeDevice, error, Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun actualizarLista(query: String = "") {
        var filtrados = allTipos

        // Aplicar filtro de estado primero (WiFi / Bluetooth / Ambos)
        when (filterState) {
            1 -> filtrados = filtrados.filter { it.soportaWifi }
            2 -> filtrados = filtrados.filter { it.soportaBluetooth }
        }

        if (query.isNotEmpty()) {
            val queryParts = query.lowercase().split("\\s+".toRegex())
            filtrados = filtrados.filter { tipo ->
                val searchTarget = "${tipo.nombreTipo} ${tipo.palabrasClaveBusqueda ?: ""}".lowercase()
                queryParts.all { part -> searchTarget.contains(part) }
            }
        }

        val isSearchingOrFiltering = query.isNotEmpty() || filterState != 0

        if (isExpanded || isSearchingOrFiltering) {
            findViewById<View>(R.id.btnVerMas).visibility = View.GONE
            adapter.submitList(filtrados)
        } else {
            if (filtrados.size > 5) {
                adapter.submitList(filtrados.take(5))
                findViewById<View>(R.id.btnVerMas).visibility = View.VISIBLE
            } else {
                adapter.submitList(filtrados)
                findViewById<View>(R.id.btnVerMas).visibility = View.GONE
            }
        }
    }

    private fun mostrarBottomSheetEleccion(tipo: com.example.android.db.AparatoTipo) {
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_connection_type, null)
        
        val btnWifi = view.findViewById<View>(R.id.btnConnectWifi)
        val btnEsp32 = view.findViewById<View>(R.id.btnConnectEsp32)
        val btnBluetooth = view.findViewById<View>(R.id.btnConnectBluetooth)
        val btnGeneric = view.findViewById<View>(R.id.btnConnectGeneric)
        
        if (!tipo.soportaWifi) btnWifi.visibility = View.GONE
        if (!tipo.soportaBluetooth) btnBluetooth.visibility = View.GONE

        btnWifi.setOnClickListener {
            bottomSheet.dismiss()
            lanzarActivityWifi(tipo)
        }
        
        btnEsp32.setOnClickListener {
            bottomSheet.dismiss()
            pendingTipoToCreate = tipo
            val intent = Intent(this, EspConfigActivity::class.java)
            intent.putExtra("EXTRA_TIPO_DISPOSITIVO", tipo.nombreTipo)
            intent.putExtra("EXTRA_ICONO_DISPOSITIVO", tipo.icono)
            espConfigLauncher.launch(intent)
        }
        
        btnBluetooth.setOnClickListener {
            bottomSheet.dismiss()
            lanzarActivityBluetooth(tipo)
        }
        
        btnGeneric.setOnClickListener {
            bottomSheet.dismiss()
            crearDispositivoGenerico(tipo)
        }
        
        bottomSheet.setContentView(view)
        bottomSheet.show()
    }

    private fun lanzarActivityBluetooth(tipo: com.example.android.db.AparatoTipo) {
        val intent = Intent(this, AddDeviceActivity::class.java)
        if (!tipo.esAsistente) {
            intent.putExtra("FILTRO_TIPO", tipo.nombreTipo)
            intent.putExtra("FILTRO_ICONO", tipo.icono)
            intent.putExtra("FILTRO_PALABRAS", tipo.palabrasClaveBusqueda)
        }
        startActivity(intent)
    }

    private fun lanzarActivityWifi(tipo: com.example.android.db.AparatoTipo) {
        val intent = Intent(this, AddDeviceWifiActivity::class.java)
        if (!tipo.esAsistente) {
            intent.putExtra("FILTRO_TIPO", tipo.nombreTipo)
            intent.putExtra("FILTRO_ICONO", tipo.icono)
            intent.putExtra("FILTRO_PALABRAS", tipo.palabrasClaveBusqueda)
        }
        startActivity(intent)
    }
}