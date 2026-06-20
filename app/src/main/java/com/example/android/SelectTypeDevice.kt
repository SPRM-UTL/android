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
import android.text.Editable
import android.text.TextWatcher
import com.example.android.network.ApiHandler
import com.example.android.network.RetrofitClient
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class SelectTypeDevice : AppCompatActivity() {

    private lateinit var adapter: AparatoTipoAdapter
    private lateinit var recyclerView: RecyclerView
    private var allTipos: List<com.example.android.db.AparatoTipo> = emptyList()
    private var isExpanded = false
    private var filterState = 0 // 0=Ambos, 1=WiFi, 2=Bluetooth

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
            if (tipo.soportaBluetooth && tipo.soportaWifi) {
                if (tipo.requiereVinculacionBluetooth) {
                    mostrarBottomSheetEleccion(tipo)
                } else {
                    iniciarProvisionamientoEsp(tipo)
                }
            } else if (tipo.soportaBluetooth) {
                if (tipo.requiereVinculacionBluetooth) {
                    lanzarActivityBluetooth(tipo)
                } else {
                    iniciarProvisionamientoEsp(tipo)
                }
            } else if (tipo.soportaWifi) {
                if (tipo.requiereVinculacionBluetooth) {
                    lanzarActivityWifi(tipo)
                } else {
                    iniciarProvisionamientoEsp(tipo)
                }
            } else {
                Toast.makeText(this, "El dispositivo no soporta conexión inalámbrica", Toast.LENGTH_SHORT).show()
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
        
        view.findViewById<View>(R.id.btnConnectWifi).setOnClickListener {
            bottomSheet.dismiss()
            lanzarActivityWifi(tipo)
        }
        
        view.findViewById<View>(R.id.btnConnectBluetooth).setOnClickListener {
            bottomSheet.dismiss()
            lanzarActivityBluetooth(tipo)
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

    private fun iniciarProvisionamientoEsp(tipo: com.example.android.db.AparatoTipo) {
        val intent = Intent(this, EspConfigActivity::class.java).apply {
            putExtra("EXTRA_MODE_ADD_DEVICE", true)
            putExtra("EXTRA_TIPO_DISPOSITIVO", tipo.nombreTipo)
            putExtra("EXTRA_ICONO_DISPOSITIVO", tipo.icono)
        }
        startActivity(intent)
    }
}