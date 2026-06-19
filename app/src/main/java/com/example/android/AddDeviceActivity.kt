package com.example.android

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.android.db.AppDatabase
import com.example.android.db.Dispositivo
import com.example.android.network.ApiHandler
import com.example.android.network.BluetoothController
import com.example.android.network.BluetoothScanManager
import com.example.android.network.RetrofitClient
import com.example.android.ui.BluetoothDeviceAdapter
import com.example.android.view.Snackbars
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddDeviceActivity : AppCompatActivity() {

    private lateinit var headerSubtitle: TextView
    private lateinit var tvDispositivoGuardado: TextView
    private lateinit var tvEstadoConexion: TextView
    private lateinit var viewStatusDot: View
    private lateinit var ivIconoEstado: ImageView
    private lateinit var ivTipoDispositivo: ImageView

    private lateinit var progressCargando: LinearProgressIndicator
    private lateinit var rvDispositivos: RecyclerView
    private lateinit var shimmerViewContainer: com.facebook.shimmer.ShimmerFrameLayout

    private lateinit var gestorBluetooth: BluetoothScanManager
    private lateinit var listAdapter: BluetoothDeviceAdapter
    private lateinit var db: AppDatabase

    private val PREF_NAME = "BluetoothPrefs"
    private val KEY_MAC = "saved_mac_address"
    private val KEY_NAME = "saved_device_name"

    private var editDeviceId: Int = -1
    private var existingDevice: Dispositivo? = null
    private var filtroTipo: String? = null
    private var filtroIcono: String? = null
    private var filtroPalabras: String? = null
    private var tiposDisponibles: List<com.example.android.db.AparatoTipo> = emptyList()

    private lateinit var switchAdvancedSearch: androidx.appcompat.widget.SwitchCompat

    private var dialogConectando: android.app.Dialog? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            verificarYEncenderBluetooth()
        } else {
            Snackbars.error(
                findViewById(android.R.id.content),
                "Permisos necesarios denegados",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) verificarGPSYScan()
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

        setContentView(R.layout.activity_add_device)

        db = AppDatabase.getDatabase(this)
        gestorBluetooth = BluetoothScanManager(this)

        val root = findViewById<View>(R.id.mainAddDevice)
        val header = findViewById<View>(R.id.header)
        val listContainer = findViewById<View>(R.id.layoutListContainer)
        val fab = findViewById<View>(R.id.fabRefresh)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            header.setPadding(
                header.paddingLeft,
                systemBars.top,
                header.paddingRight,
                (12 * resources.displayMetrics.density).toInt()
            )
            listContainer.setPadding(
                listContainer.paddingLeft,
                listContainer.paddingTop,
                listContainer.paddingRight,
                systemBars.bottom + (80 * resources.displayMetrics.density).toInt()
            )
            val params = fab.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.bottomMargin = systemBars.bottom + (20 * resources.displayMetrics.density).toInt()
            fab.layoutParams = params
            insets
        }

        editDeviceId = intent.getIntExtra("EXTRA_DEVICE_ID", -1)
        filtroTipo = intent.getStringExtra("FILTRO_TIPO")
        filtroIcono = intent.getStringExtra("FILTRO_ICONO")
        filtroPalabras = intent.getStringExtra("FILTRO_PALABRAS")

        cargarTiposDesdeServidor()

        inicializarVistas()
        configurarUI()
        configurarEventos()

        if (editDeviceId != -1) {
            cargarDatosDispositivo()
        }
        pedirPermisos()
    }

    // ==========================================================
    // INICIALIZACION
    // ==========================================================

    private fun inicializarVistas() {
        tvDispositivoGuardado = findViewById(R.id.tvDispositivoGuardado)
        tvEstadoConexion = findViewById(R.id.tvEstadoConexion)
        viewStatusDot = findViewById(R.id.viewStatusDot)
        ivIconoEstado = findViewById(R.id.ivIconoEstado)
        ivTipoDispositivo = findViewById(R.id.ivTipoDispositivo)
        progressCargando = findViewById(R.id.progressCargando)
        rvDispositivos = findViewById(R.id.rvDispositivosBt)
        shimmerViewContainer = findViewById(R.id.shimmerViewContainer)
        switchAdvancedSearch = findViewById(R.id.switchAdvancedSearch)
        headerSubtitle = findViewById(R.id.tvHeaderSubtitle)
    }

    private fun configurarUI() {
        listAdapter = BluetoothDeviceAdapter { resultado ->
            val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(resultado.mac)
            intentarConectarDispositivo(device)
        }

        rvDispositivos.layoutManager = LinearLayoutManager(this)
        rvDispositivos.adapter = listAdapter

        if (filtroIcono != null) {
            val resId = resources.getIdentifier(filtroIcono, "drawable", packageName)
            if (resId != 0) {
                ivTipoDispositivo.setImageResource(resId)
                ivTipoDispositivo.visibility = View.VISIBLE
            } else {
                ivTipoDispositivo.visibility = View.GONE
            }
        } else {
            ivTipoDispositivo.visibility = View.GONE
        }

        actualizarUIEstado()
    }

    private fun configurarEventos() {
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        findViewById<View>(R.id.fabRefresh).setOnClickListener { 
            pedirPermisos() 
        }
        switchAdvancedSearch.setOnCheckedChangeListener { _, _ -> pedirPermisos() }
    }

    // ==========================================================
    // DATOS
    // ==========================================================

    private fun cargarTiposDesdeServidor() {
        val sharedPreferences = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("apiToken", null)

        if (!token.isNullOrEmpty()) {
            lifecycleScope.launch {
                ApiHandler.safeApiCall(
                    activity = this@AddDeviceActivity,
                    showLoading = false,
                    loadingTitle = "",
                    loadingMessage = "",
                    apiCall = { RetrofitClient.deviceService.getTiposAparato("Bearer $token") },
                    onSuccess = { apiResponse ->
                        if (apiResponse.success && apiResponse.data != null) {
                            tiposDisponibles = apiResponse.data
                        }
                    },
                    onError = {
                        // Opcional: manejar error en background
                    }
                )
            }
        }
    }

    private fun cargarDatosDispositivo() {
        lifecycleScope.launch {
            val dev = db.dispositivoDao().getDispositivoById(editDeviceId)
            existingDevice = dev
            dev?.let {
                if (!it.nombreBluetooth.isNullOrEmpty()) {
                    tvDispositivoGuardado.text = it.nombreBluetooth
                } else if (!it.macBluetooth.isNullOrEmpty()) {
                    tvDispositivoGuardado.text = it.macBluetooth
                }
                mostrarAlertaConfiguracion(null, it)
            }
        }
    }

    // ==========================================================
    // BLUETOOTH
    // ==========================================================

    private fun pedirPermisos() {
        if (gestorBluetooth.tienePermisosNecesarios()) {
            verificarYEncenderBluetooth()
        } else {
            requestPermissionLauncher.launch(gestorBluetooth.permisosNecesarios())
        }
    }

    @SuppressLint("MissingPermission")
    private fun verificarYEncenderBluetooth() {
        if (!gestorBluetooth.bluetoothActivado) {
            try {
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } catch (_: Exception) {}
        } else {
            verificarGPSYScan()
        }
    }

    private fun verificarGPSYScan() {
        if (!gestorBluetooth.isGpsEnabled()) {
            Snackbars.warning(
                findViewById(android.R.id.content),
                "Activa el GPS para escanear",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        } else {
            iniciarEscaneo()
        }
    }

    @SuppressLint("MissingPermission")
    private fun iniciarEscaneo() {
        listAdapter.limpiar()
        headerSubtitle.text = "Buscando dispositivos cercanos..."
        progressCargando.visibility = View.VISIBLE
        rvDispositivos.visibility = View.GONE
        shimmerViewContainer.visibility = View.VISIBLE
        shimmerViewContainer.startShimmer()

        gestorBluetooth.iniciarEscaneo(
            alEncontrarDispositivo = { resultado ->
                runOnUiThread {
                    val deviceLog = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(resultado.mac)
                    val clase = deviceLog.bluetoothClass?.deviceClass
                    android.util.Log.d("BT_FILTRO", "Nombre: ${resultado.nombre} | MAC: ${resultado.mac} | Clase: $clase")

                    val pasaFiltro = if (switchAdvancedSearch.isChecked) {
                        true
                    } else {
                        val pasaHardware = when (filtroTipo) {
                            "Audífonos" -> esDispositivoAudio(resultado.mac, resultado.nombre)
                            "Bocinas"   -> esDispositivoBocina(resultado.mac, resultado.nombre)
                            else        -> false
                        }

                        val pasaPalabras = esDispositivoPorPalabras(resultado.nombre, filtroPalabras)
                        val sinFiltrosBackend = filtroPalabras.isNullOrEmpty()

                        pasaHardware || pasaPalabras || sinFiltrosBackend
                    }

                    if (pasaFiltro) {
                        if (shimmerViewContainer.visibility == View.VISIBLE) {
                            shimmerViewContainer.stopShimmer()
                            shimmerViewContainer.visibility = View.GONE
                            rvDispositivos.visibility = View.VISIBLE
                        }
                        listAdapter.agregarDispositivo(resultado)
                        headerSubtitle.text = "Activado • ${listAdapter.itemCount} dispositivos"
                    }
                }
            },
            alFinalizarEscaneo = {
                runOnUiThread {
                    if (isDestroyed || isFinishing) return@runOnUiThread
                    progressCargando.visibility = View.INVISIBLE
                    if (shimmerViewContainer.visibility == View.VISIBLE) {
                        shimmerViewContainer.stopShimmer()
                        shimmerViewContainer.visibility = View.GONE
                        rvDispositivos.visibility = View.VISIBLE
                    }
                }
            }
        )
    }

    private fun esDispositivoPorPalabras(nombre: String?, palabrasStr: String?): Boolean {
        if (palabrasStr.isNullOrEmpty()) return false
        val nombreUpper = nombre?.uppercase() ?: return false
        val palabras = palabrasStr.split(",").map { it.trim().uppercase() }
        for (palabra in palabras) {
            if (palabra.isNotEmpty() && nombreUpper.contains(palabra)) {
                return true
            }
        }
        return false
    }

    @SuppressLint("MissingPermission")
    private fun esDispositivoAudio(mac: String, nombre: String?): Boolean {
        return try {
            val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(mac)
            val claseDispositivo = device.bluetoothClass?.deviceClass

            android.util.Log.d("BT_FILTRO", "Clase obtenida en esDispositivoAudio: $claseDispositivo")

            val esPorClase = claseDispositivo == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES ||
                    claseDispositivo == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE ||
                    claseDispositivo == BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO

            val nombreUpper = nombre?.uppercase() ?: ""
            val esPorNombre = nombreUpper.contains("HEADPHONE") ||
                    nombreUpper.contains("HEADSET") ||
                    nombreUpper.contains("EARPHONE") ||
                    nombreUpper.contains("EARBUDS") ||
                    nombreUpper.contains("AUDIFONOS") ||
                    nombreUpper.contains("AUDÍFONOS") ||
                    nombreUpper.contains("AURICULAR") ||
                    nombreUpper.contains("AUT") ||
                    nombreUpper.contains("TWS") ||
                    nombreUpper.contains("BUDS") ||
                    nombreUpper.contains("AIRPOD") ||
                    nombreUpper.contains("WH-") ||
                    nombreUpper.contains("WF-") ||
                    nombreUpper.contains("QC") ||
                    nombreUpper.contains("TUNE") ||
                    nombreUpper.contains("FREEBUDS")

            android.util.Log.d("BT_FILTRO", "esPorClase: $esPorClase | esPorNombre: $esPorNombre")

            esPorClase || esPorNombre
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun esDispositivoBocina(mac: String, nombre: String?): Boolean {
        return try {
            val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(mac)
            val claseDispositivo = device.bluetoothClass?.deviceClass

            val esPorClase = claseDispositivo == BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER ||
                    claseDispositivo == BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO ||
                    claseDispositivo == BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO

            val nombreUpper = nombre?.uppercase() ?: ""
            val esPorNombre = nombreUpper.contains("SPEAKER") ||
                    nombreUpper.contains("BOCINA") ||
                    nombreUpper.contains("ALTAVOZ") ||
                    nombreUpper.contains("SOUNDBAR") ||
                    nombreUpper.contains("CHARGE") ||
                    nombreUpper.contains("FLIP") ||
                    nombreUpper.contains("XTREME") ||
                    nombreUpper.contains("BOOMBOX") ||
                    nombreUpper.contains("SOUNDLINK")

            esPorClase || esPorNombre
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun intentarConectarDispositivo(device: BluetoothDevice) {
        gestorBluetooth.detenerEscaneo()

        // Mostrar diálogo de conexión
        val themedContext = ContextThemeWrapper(this, com.google.android.material.R.style.Theme_Material3_Light_Dialog)
        val dialogView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_connecting, null)

        dialogView.findViewById<TextView>(R.id.tvConectandoNombre).text =
            device.name ?: "Dispositivo Bluetooth"

        dialogConectando = MaterialAlertDialogBuilder(themedContext)
            .setView(dialogView)
            .setCancelable(false)
            .create()
            .also {
                it.window?.setBackgroundDrawable(null) // <- cambia ColorDrawable(TRANSPARENT) por null
                it.show()
            }

        val job = lifecycleScope.launch(Dispatchers.IO) {
            var exito = false
            repeat(2) {
                if (!exito) {
                    exito = BluetoothController.connectDevice(device)
                    if (!exito) delay(1500)
                }
            }

            withContext(Dispatchers.Main) {
                dialogConectando?.dismiss()
                dialogConectando = null

                if (exito) {
                    mostrarAlertaConfiguracion(device)
                    Snackbars.success(
                        findViewById(android.R.id.content),
                        "¡Vínculo Establecido!",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    MaterialAlertDialogBuilder(this@AddDeviceActivity, com.google.android.material.R.style.Theme_Material3_Light_Dialog)
                        .setTitle("Error de conexión")
                        .setMessage("No se pudo conectar con el dispositivo. Asegúrate de que esté encendido, cerca de tu teléfono y en modo emparejamiento.")
                        .setPositiveButton("Aceptar", null)
                        .show()
                }
                actualizarUIEstado()
            }
        }

        dialogView.findViewById<View>(R.id.btnCancelConnecting)?.setOnClickListener {
            job.cancel()
            dialogConectando?.dismiss()
            dialogConectando = null
            Snackbars.warning(findViewById(android.R.id.content), "Conexión cancelada", Toast.LENGTH_SHORT).show()
            // Retomar el escaneo si se canceló
            iniciarEscaneo()
        }
    }

    // ==========================================================
    // DIALOGO
    // ==========================================================

    private fun actualizarIconoTipo(tipo: String, ivTipoIcono: ImageView) {
        val tipoObj = tiposDisponibles.find { it.nombreTipo == tipo }
        val iconName = tipoObj?.icono ?: "ic_default"
        
        val resId = resources.getIdentifier(iconName, "drawable", packageName)
        if (resId != 0 && iconName != "ic_default") {
            ivTipoIcono.setImageResource(resId)
            ivTipoIcono.visibility = View.VISIBLE
        } else {
            // Fallback hardcoded just in case API hasn't loaded yet
            val iconRes = when (tipo) {
                "Audífonos"  -> R.drawable.headphones
                "Bocinas"    -> R.drawable.speaker
                "Focos"      -> R.drawable.lightbulb
                "Luces"      -> R.drawable.lightbulb
                "Ventilador" -> R.drawable.wind
                "Televisión" -> R.drawable.tv_minimal
                else         -> null
            }
            if (iconRes != null) {
                ivTipoIcono.setImageResource(iconRes)
                ivTipoIcono.visibility = View.VISIBLE
            } else {
                ivTipoIcono.visibility = View.GONE
            }
        }
    }

    private fun mostrarAlertaConfiguracion(device: BluetoothDevice?, editDevParam: Dispositivo? = null) {
        val themedContext = ContextThemeWrapper(this, com.google.android.material.R.style.Theme_Material3_Light_Dialog)
        val dialogView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_config_device, null)

        val dialog = MaterialAlertDialogBuilder(themedContext)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTarget = dialogView.findViewById<TextView>(R.id.tvDeviceTarget)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etDeviceName)
        val etType = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.etDeviceType)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
        val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btnConfirm)
        val btnDelete = dialogView.findViewById<MaterialButton>(R.id.btnDeleteDialog)
        val ivTipoIcono = dialogView.findViewById<ImageView>(R.id.ivTipoIcono)

        @SuppressLint("MissingPermission")
        val hardwareName = if (device != null) {
            device.name ?: "Hardware Encontrado"
        } else {
            editDevParam?.nombreBluetooth ?: "Dispositivo Vinculado"
        }
        tvTarget.text = hardwareName

        val tipos = if (filtroTipo != null) {
            listOf(filtroTipo!!)
        } else if (tiposDisponibles.isNotEmpty()) {
            tiposDisponibles.map { it.nombreTipo }
        } else {
            listOf("Focos", "Bocinas", "Ventilador", "Televisión", "Audífonos")
        }

        val adapter = ArrayAdapter(themedContext, android.R.layout.simple_spinner_dropdown_item, tipos)
        etType.setAdapter(adapter)
        etType.setText(tipos[0], false)
        etType.isEnabled = filtroTipo == null
        etType.setOnClickListener {
            if (etType.isEnabled) etType.showDropDown()
        }

        etType.setOnItemClickListener { _, _, _, _ ->
            actualizarIconoTipo(etType.text.toString(), ivTipoIcono)
        }

        actualizarIconoTipo(tipos[0], ivTipoIcono)

        if (editDevParam != null) {
            etName.setText(editDevParam.nombre)
            val tipoEdit = editDevParam.tipo ?: ""
            etType.setText(tipoEdit, false)
            actualizarIconoTipo(tipoEdit, ivTipoIcono)
            btnConfirm.text = "Actualizar"
            btnDelete.visibility = View.VISIBLE
        } else {
            etName.setText(hardwareName)
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnDelete.setOnClickListener {
            if (editDevParam != null) startDeleteDevice(editDevParam.id)
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            val finalName = etName.text.toString().trim()
            val finalType = etType.text.toString().trim()

            if (finalName.isEmpty()) {
                etName.error = "Ingresa un nombre"
                return@setOnClickListener
            }

            dialog.dismiss()

            val mac = device?.address ?: editDevParam?.macBluetooth
            val nombreBt = device?.let {
                @SuppressLint("MissingPermission")
                it.name
            } ?: editDevParam?.nombreBluetooth

            val nuevo = Dispositivo(
                id = editDevParam?.id ?: 0,
                nombre = finalName,
                tipo = finalType.ifBlank { "General" },
                accion = editDevParam?.accion ?: "Encendido",
                comandoBluetooth = editDevParam?.comandoBluetooth ?: "BT_ON",
                icono = editDevParam?.icono ?: "ic_default",
                macBluetooth = mac,
                nombreBluetooth = nombreBt,
                fechaSincronizacion = java.time.LocalDateTime.now().toString()
            )

            tvDispositivoGuardado.text = finalName
            guardarEnServidor(nuevo)
        }

        dialog.show()
    }

    // ==========================================================
    // SERVIDOR
    // ==========================================================

    private fun startDeleteDevice(id: Int) {
        lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = this@AddDeviceActivity,
                showLoading = true,
                loadingTitle = "Eliminando",
                loadingMessage = "Eliminando dispositivo...",
                apiCall = {
                    val token = getSharedPreferences("SesionApp", Context.MODE_PRIVATE).getString("apiToken", "") ?: ""
                    RetrofitClient.deviceService.deleteDispositivo("Bearer $token", id)
                },
                onSuccess = {
                    existingDevice?.let { db.dispositivoDao().deleteDispositivo(it) }
                    Snackbars.success(findViewById(android.R.id.content), "Dispositivo eliminado", Toast.LENGTH_SHORT).show()
                },
                onError = { errorMsg ->
                    Snackbars.error(findViewById(android.R.id.content), errorMsg, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun guardarEnServidor(dispositivo: Dispositivo) {
        lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = this@AddDeviceActivity,
                showLoading = true,
                loadingTitle = "Guardando",
                loadingMessage = "Sincronizando dispositivo...",
                apiCall = {
                    val token = getSharedPreferences("SesionApp", Context.MODE_PRIVATE).getString("apiToken", "") ?: ""
                    val bearer = "Bearer $token"
                    if (dispositivo.id == 0) {
                        RetrofitClient.deviceService.createDispositivo(bearer, dispositivo)
                    } else {
                        val updateResp = RetrofitClient.deviceService.updateDispositivo(bearer, dispositivo.id, dispositivo)
                        if (updateResp.isSuccessful) {
                            retrofit2.Response.success(com.example.android.network.ApiResponse(true, 200, dispositivo))
                        } else {
                            retrofit2.Response.error(updateResp.code(), updateResp.errorBody()!!)
                        }
                    }
                },
                onSuccess = { response ->
                    val guardado = response.data
                    if (guardado != null) {
                        db.dispositivoDao().insertDispositivo(guardado)
                    }
                    Snackbars.success(findViewById(android.R.id.content), "Guardado exitoso", Toast.LENGTH_SHORT).show()
                },
                onError = { errorMsg ->
                    Snackbars.error(findViewById(android.R.id.content), errorMsg, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    // ==========================================================
    // HELPERS
    // ==========================================================

    private fun actualizarUIEstado() {
        if (BluetoothController.isConnected) {
            tvEstadoConexion.text = "Conectado"
            tvEstadoConexion.setTextColor(ContextCompat.getColor(this, R.color.teal_primary))
            viewStatusDot.background = ContextCompat.getDrawable(this, R.drawable.dot_green)
            ivIconoEstado.setImageResource(android.R.drawable.presence_online)
            ivIconoEstado.setColorFilter(ContextCompat.getColor(this, R.color.teal_primary))
        } else {
            tvEstadoConexion.text = "Desconectado"
            tvEstadoConexion.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            viewStatusDot.background = ContextCompat.getDrawable(this, R.drawable.dot_red)
            ivIconoEstado.setImageResource(android.R.drawable.presence_offline)
            ivIconoEstado.setColorFilter(ContextCompat.getColor(this, android.R.color.darker_gray))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gestorBluetooth.detenerEscaneo()
        dialogConectando?.dismiss()
    }
}