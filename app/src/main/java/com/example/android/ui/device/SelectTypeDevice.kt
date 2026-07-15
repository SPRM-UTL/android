package com.example.android.ui.device

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import com.example.android.ui.network.EspConfigActivity
import com.example.android.ui.adapters.AparatoTipoAdapter

class SelectTypeDevice : AppCompatActivity() {

    private lateinit var adapter: AparatoTipoAdapter
    private lateinit var recyclerView: RecyclerView

    private var allTipos: List<com.example.android.db.AparatoTipo> = emptyList()
    private var isExpanded = false
    private var filterState = 0

    private val espConfigLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Dispositivo registrado correctamente", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private val wifiConfigLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Dispositivo Wi-Fi registrado correctamente", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private val bluetoothConfigLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Dispositivo Bluetooth registrado correctamente", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

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
            when {
                tipo.soportaWifi && tipo.soportaBluetooth && tipo.requiereVinculacionBluetooth -> {
                    mostrarBottomSheetVinculacion(tipo)
                }
                tipo.soportaWifi && tipo.soportaBluetooth -> {
                    mostrarBottomSheetEleccion(tipo)
                }
                tipo.requiereVinculacionBluetooth -> {
                    lanzarEspConfig(tipo, "ESP32")
                }
                tipo.soportaWifi -> {
                    lanzarActivityWifi(tipo)
                }
                tipo.soportaBluetooth -> {
                    Toast.makeText(
                        this,
                        "Vinculación Bluetooth genérica en desarrollo",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                else -> {
                    lanzarEspConfig(tipo, "ESP32")
                }
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
            filterState = (filterState + 1) % 4
            when (filterState) {
                0 -> searchLayout.endIconDrawable = getDrawable(R.drawable.ic_filter_all)
                1 -> searchLayout.endIconDrawable = getDrawable(R.drawable.wifi)
                2 -> searchLayout.endIconDrawable = getDrawable(R.drawable.bluetooth)
                3 -> searchLayout.endIconDrawable = getDrawable(R.drawable.ic_link)
            }
            actualizarLista(etSearch.text?.toString() ?: "")
        }

        cargarTiposDispositivos()
    }

    private fun lanzarEspConfig(tipo: com.example.android.db.AparatoTipo, metodoVinculacion: String) {
        val intent = Intent(this, EspConfigActivity::class.java).apply {
            putExtra(EspConfigActivity.EXTRA_TIPO_DISPOSITIVO, tipo.nombreTipo)
            putExtra(EspConfigActivity.EXTRA_ICONO_DISPOSITIVO, tipo.icono)
            putExtra(EspConfigActivity.EXTRA_MODO_SOCKET, true)
            putExtra("EXTRA_METODO_VINCULACION", metodoVinculacion)
        }
        espConfigLauncher.launch(intent)
    }

    private fun lanzarActivityWifi(tipo: com.example.android.db.AparatoTipo) {
        val intent = Intent(this, AddDeviceWifiActivity::class.java)
        if (!tipo.esAsistente) {
            intent.putExtra(AddDeviceWifiActivity.EXTRA_FILTRO_TIPO, tipo.nombreTipo)
            intent.putExtra(AddDeviceWifiActivity.EXTRA_FILTRO_ICONO, tipo.icono)
            intent.putExtra(AddDeviceWifiActivity.EXTRA_FILTRO_PALABRAS, tipo.palabrasClaveBusqueda)
        }
        wifiConfigLauncher.launch(intent)
    }

    private fun lanzarActivityBluetooth(tipo: com.example.android.db.AparatoTipo?) {
        val intent = Intent(this, AddDeviceBluetoothActivity::class.java)
        if (tipo != null) {
            intent.putExtra(AddDeviceBluetoothActivity.EXTRA_FILTRO_TIPO, tipo.nombreTipo)
            intent.putExtra(AddDeviceBluetoothActivity.EXTRA_FILTRO_ICONO, tipo.icono)
            intent.putExtra(AddDeviceBluetoothActivity.EXTRA_FILTRO_PALABRAS, tipo.palabrasClaveBusqueda)
        }
        bluetoothConfigLauncher.launch(intent)
    }

    private fun mostrarBottomSheetEleccion(tipo: com.example.android.db.AparatoTipo) {
        val bottomSheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_connection_type, null)

        view.findViewById<View>(R.id.btnConnectWifi).setOnClickListener {
            bottomSheet.dismiss()
            lanzarActivityWifi(tipo)
        }

        view.findViewById<View>(R.id.btnConnectBluetooth).setOnClickListener {
            bottomSheet.dismiss()
            lanzarActivityBluetooth(tipo)
        }

        view.findViewById<View>(R.id.btnConnectEsp32)?.setOnClickListener {
            bottomSheet.dismiss()
            lanzarEspConfig(tipo, "ESP32")
        }

        view.findViewById<View>(R.id.btnConnectGeneric)?.visibility = View.GONE

        bottomSheet.setContentView(view)
        bottomSheet.show()
    }

    private fun mostrarBottomSheetVinculacion(tipo: com.example.android.db.AparatoTipo) {
        val bottomSheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_pairing_type, null)

        val btnWifi = view.findViewById<View>(R.id.btnWifi)
        val btnBluetooth = view.findViewById<View>(R.id.btnBluetooth)
        val btnEsp32 = view.findViewById<View>(R.id.btnEsp32)

        if (tipo.soportaWifi) btnWifi.visibility = View.VISIBLE
        if (tipo.soportaBluetooth) btnBluetooth.visibility = View.VISIBLE
        if (tipo.requiereVinculacionBluetooth) btnEsp32.visibility = View.VISIBLE

        btnWifi.setOnClickListener {
            bottomSheet.dismiss()
            lanzarActivityWifi(tipo)
        }

        btnBluetooth.setOnClickListener {
            bottomSheet.dismiss()
            lanzarActivityBluetooth(tipo)
        }

        btnEsp32.setOnClickListener {
            bottomSheet.dismiss()
            lanzarEspConfig(tipo, "ESP32")
        }

        bottomSheet.setContentView(view)
        bottomSheet.show()
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

        when (filterState) {
            1 -> filtrados = filtrados.filter { it.soportaWifi }
            2 -> filtrados = filtrados.filter { it.soportaBluetooth }
            3 -> filtrados = filtrados.filter { it.requiereVinculacionBluetooth }
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
}
