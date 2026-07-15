package com.example.android.feature.network_config
import com.example.android.core.ui.adapters.DeviceAdapter
import com.example.android.BuildConfig

import com.example.android.R

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.android.core.db.AppDatabase
import com.example.android.core.db.Dispositivo
import com.example.android.core.network.ApiHandler
import com.example.android.core.network.ConfiguracionRedRequest
import com.example.android.core.network.RetrofitClient
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import com.example.android.feature.device.PortraitCaptureActivity

class EspConfigActivity : AppCompatActivity() {

    // --- Header / step indicator ---
    private lateinit var cardBack: MaterialCardView
    private lateinit var stepDot1: FrameLayout
    private lateinit var stepDot2: FrameLayout
    private lateinit var stepDot3: FrameLayout
    private lateinit var stepLine1: View
    private lateinit var stepLine2: View
    private lateinit var stepLabel1: TextView
    private lateinit var stepLabel2: TextView
    private lateinit var stepLabel3: TextView

    // --- Step content containers ---
    private lateinit var stepContent1: LinearLayout
    private lateinit var stepContent2: LinearLayout
    private lateinit var stepContent3: LinearLayout

    // --- Container general con Scroll ---
    private lateinit var scrollContent: ScrollView

    // --- Step 1: buscar dispositivo ---
    private lateinit var cardEstado: MaterialCardView
    private lateinit var iconEstadoBg: FrameLayout // <-- DECLARADA AQUÍ CORRECTAMENTE
    private lateinit var ivEstadoConexion: ImageView
    private lateinit var tvEstadoConexion: TextView
    private lateinit var tvSubEstadoConexion: TextView
    private lateinit var btnBuscarDispositivos: MaterialButton
    private lateinit var tvStep1Title: TextView
    private lateinit var tvStep1Subtitle: TextView
    private lateinit var tvStep2Title: TextView
    private lateinit var tvStep2Subtitle: TextView
    private lateinit var tvStep3Title: TextView
    private lateinit var tvStep3Subtitle: TextView
    private lateinit var tvTipoResumen: TextView

    private var bottomSheetDialog: com.google.android.material.bottomsheet.BottomSheetDialog? = null
    private var lvDispositivos: ListView? = null
    private var progressScan: LinearProgressIndicator? = null
    private var tvSinDispositivos: TextView? = null

    // --- Step 2: wifi ---
    private lateinit var cardOptionWifi: MaterialCardView
    private lateinit var cardOptionManual: MaterialCardView
    private lateinit var cardOptionQr: MaterialCardView
    private lateinit var etSsid: TextInputEditText
    private lateinit var etPassword: TextInputEditText

    // --- Step 3: confirmar ---
    private lateinit var tvResumenDispositivo: TextView
    private lateinit var cardSeleccionWifi: MaterialCardView
    private lateinit var tvSelectedSsid: TextView
    private lateinit var btnEditarWifi: TextView

    // --- Bottom action bar ---
    private lateinit var bottomActionBar: LinearLayout
    private lateinit var btnAtras: MaterialButton
    private lateinit var btnSiguiente: MaterialButton

    private lateinit var btnBack: ImageButton

    private lateinit var mainConfig: View

    // Paso actual del wizard: 1, 2 o 3
    private var pasoActual: Int = 1

    // Datos Wi-Fi actuales en memoria
    private var currentSsidInput = ""
    private var currentPasswordInput = ""

    // Bluetooth
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var wifiCharacteristic: BluetoothGattCharacteristic? = null
    private var ipCharacteristic: BluetoothGattCharacteristic? = null

    // Listas
    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private lateinit var listAdapter: DeviceAdapter

    // Base de datos local y datos del ESP32 vinculado
    private lateinit var db: AppDatabase
    private var aparatosLocales: List<Dispositivo> = emptyList()
    private var connectedEspMac: String = ""
    private var connectedEspName: String = ""
    private var connectedEspIp: String = ""
    private var aparatoSeleccionadoIdx: Int = -1
    private var ipRecibida = false
    private var dialogEspMostrado = false

    private var tipoDispositivo: String = ""
    private var iconoDispositivo: String? = null
    private var dispositivoGuardadoEnBd = false
    private var aparatoIdRegistrado: Int? = null
    private val registroMutex = Mutex()

    // Constantes
    companion object {
        private val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        private val WIFI_CHAR_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a9")
        private val IP_CHAR_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26aa")
        private const val SCAN_PERIOD: Long = 10000
        private const val PREF_NAME = "EspConfigPrefs"
        private const val KEY_MAC = "saved_mac_address"
        private const val KEY_NAME = "saved_device_name"
        const val EXTRA_TIPO_DISPOSITIVO = "EXTRA_TIPO_DISPOSITIVO"
        const val EXTRA_ICONO_DISPOSITIVO = "EXTRA_ICONO_DISPOSITIVO"
        const val EXTRA_MODO_SOCKET = "EXTRA_MODO_SOCKET"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false
    private var isConnected = false

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (!discoveredDevices.any { it.address == device.address }) {
                discoveredDevices.add(device)
                listAdapter.notifyDataSetChanged()
                tvSinDispositivos?.visibility = View.GONE
            }
        }

        override fun onScanFailed(errorCode: Int) {
            runOnUiThread {
                detenerEscaneo()
                mostrarSnackbar("Error en escaneo: $errorCode", true)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            verificarBluetoothEncendido()
        } else {
            mostrarSnackbar("Activa los permisos de Bluetooth y ubicación para buscar tu ESP32", true)
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            iniciarEscaneoBle()
        } else {
            mostrarSnackbar("Necesitas Bluetooth activado para conectar tu ESP32", false)
        }
    }

    private val qrScanLauncher = registerForActivityResult(
        com.journeyapps.barcodescanner.ScanContract()
    ) { result ->
        if (result.contents != null) {
            procesarContenidoQr(result.contents)
        } else {
            mostrarSnackbar("Escaneo cancelado", true)
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

        setContentView(R.layout.activity_esp_config)
        mainConfig = findViewById(R.id.mainConfig)

        ViewCompat.setOnApplyWindowInsetsListener(mainConfig) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            val bottomPadding = if (ime.bottom > 0) ime.bottom else bars.bottom
            view.setPadding(bars.left, 0, bars.right, bottomPadding)

            cardBack.getChildAt(0)?.setPadding(0, bars.top, 0, 0)
            insets
        }

        inicializarVistas()
        inicializarBluetooth()
        configurarListeners()
        mostrarPaso(1)

        tipoDispositivo = intent.getStringExtra(EXTRA_TIPO_DISPOSITIVO) ?: ""
        iconoDispositivo = intent.getStringExtra(EXTRA_ICONO_DISPOSITIVO)

        val etiqueta = if (tipoDispositivo.isNotBlank()) tipoDispositivo.lowercase() else "dispositivo ESP32"
        val capitalizedTipo = etiqueta.replaceFirstChar { it.uppercase() }

        findViewById<TextView>(R.id.tvTituloConfig).text = "Configurar $etiqueta"

        tvStep1Title.text = "Busca tu $etiqueta"
        tvStep1Subtitle.text = "Acerca tu teléfono al ESP32 ($etiqueta) y activa Bluetooth para detectarlo."
        tvStep2Title.text = "Conecta tu $etiqueta al Wi-Fi"
        tvStep2Subtitle.text = "Ingresa los datos de tu red Wi-Fi para que el ESP32 se conecte al servidor."
        tvStep3Title.text = "Confirma y envía"
        tvStep3Subtitle.text = "Revisa los datos antes de enviarlos a tu ESP32."
        tvTipoResumen.text = capitalizedTipo
        tvSubEstadoConexion.text = "Aún no se ha encontrado tu $etiqueta."

        db = AppDatabase.getDatabase(this)
        cargarAparatosLocales()

        verificarEstadoConexionPrevia()
    }

    private fun verificarEstadoConexionPrevia() {
        val sharedPref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedMac = sharedPref.getString(KEY_MAC, "") ?: ""
        if (savedMac.isNotBlank()) {
            lifecycleScope.launch {
                try {
                    val response = RetrofitClient.deviceService.getWsStatus(savedMac)
                    if (response.isSuccessful && response.body()?.connected == true) {
                        actualizarEstadoConexion(true)
                        tvSubEstadoConexion.text = "Tu ESP32 ya está conectado a la red Wi-Fi."
                        btnBuscarDispositivos.text = "Configurar otra red"
                    }
                } catch (e: Exception) {
                    // Ignorar
                }
            }
        }
    }

    private fun inicializarVistas() {
        cardBack = findViewById(R.id.cardBack)
        btnBack = findViewById(R.id.btnBack)

        stepDot1 = findViewById(R.id.stepDot1)
        stepDot2 = findViewById(R.id.stepDot2)
        stepDot3 = findViewById(R.id.stepDot3)
        stepLine1 = findViewById(R.id.stepLine1)
        stepLine2 = findViewById(R.id.stepLine2)
        stepLabel1 = findViewById(R.id.stepLabel1)
        stepLabel2 = findViewById(R.id.stepLabel2)
        stepLabel3 = findViewById(R.id.stepLabel3)

        scrollContent = findViewById(R.id.scrollContent)
        stepContent1 = findViewById(R.id.stepContent1)
        stepContent2 = findViewById(R.id.stepContent2)
        stepContent3 = findViewById(R.id.stepContent3)

        cardEstado = findViewById(R.id.cardEstado)
        iconEstadoBg = findViewById(R.id.iconEstadoBg) // Vinculada correctamente aquí
        ivEstadoConexion = findViewById(R.id.ivEstadoConexion)
        tvEstadoConexion = findViewById(R.id.tvEstadoConexion)
        tvSubEstadoConexion = findViewById(R.id.tvSubEstadoConexion)
        btnBuscarDispositivos = findViewById(R.id.btnBuscarDispositivos)

        tvStep1Title = findViewById(R.id.tvStep1Title)
        tvStep1Subtitle = findViewById(R.id.tvStep1Subtitle)
        tvStep2Title = findViewById(R.id.tvStep2Title)
        tvStep2Subtitle = findViewById(R.id.tvStep2Subtitle)
        tvStep3Title = findViewById(R.id.tvStep3Title)
        tvStep3Subtitle = findViewById(R.id.tvStep3Subtitle)
        tvTipoResumen = findViewById(R.id.tvTipoResumen)

        listAdapter = DeviceAdapter(this, discoveredDevices)

        cardOptionWifi = findViewById(R.id.cardOptionWifi)
        cardOptionManual = findViewById(R.id.cardOptionManual)
        cardOptionQr = findViewById(R.id.cardOptionQr)
        etSsid = findViewById(R.id.etSsid)
        etPassword = findViewById(R.id.etPassword)

        val focusScrollListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                handler.postDelayed({
                    scrollContent.smoothScrollTo(0, scrollContent.bottom)
                }, 200)
            }
        }
        etSsid.onFocusChangeListener = focusScrollListener
        etPassword.onFocusChangeListener = focusScrollListener

        tvResumenDispositivo = findViewById(R.id.tvResumenDispositivo)
        cardSeleccionWifi = findViewById(R.id.cardSeleccionWifi)
        tvSelectedSsid = findViewById(R.id.tvSelectedSsid)
        btnEditarWifi = findViewById(R.id.btnEditarWifi)

        bottomActionBar = findViewById(R.id.bottomActionBar)
        btnAtras = findViewById(R.id.btnAtras)
        btnSiguiente = findViewById(R.id.btnSiguiente)
    }

    private inner class DeviceAdapter(context: Context, private val devices: List<BluetoothDevice>) :
        ArrayAdapter<BluetoothDevice>(context, 0, devices) {

        @SuppressLint("MissingPermission")
        override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_bluetooth_device, parent, false)
            val device = getItem(position)

            val tvDeviceName = view.findViewById<TextView>(R.id.tvBtDeviceName)
            val tvDeviceMac = view.findViewById<TextView>(R.id.tvBtDeviceMac)
            val btnConectar = view.findViewById<MaterialButton>(R.id.btnItemConectar)

            tvDeviceName.text = device?.name ?: "Dispositivo desconocido"
            tvDeviceMac.text = device?.address ?: "00:00:00:00:00:00"

            btnConectar.setOnClickListener {
                bottomSheetDialog?.dismiss()
                device?.let { conectarADispositivo(it) }
            }

            return view
        }
    }

    private fun inicializarBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
    }

    private fun configurarListeners() {
        btnBack.setOnClickListener {
            if (pasoActual > 1) {
                mostrarPaso(pasoActual - 1)
            } else {
                onBackPressedDispatcher.onBackPressed()
            }
        }

        btnBuscarDispositivos.setOnClickListener {
            mostrarBottomSheetDispositivos()
            if (tienePermisosNecesarios()) {
                verificarBluetoothEncendido()
            } else {
                pedirPermisos()
            }
        }

        cardOptionWifi.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                autocompletarRedActual()
            } else {
                pedirPermisosLocationParaWifi()
            }
        }

        cardOptionManual.setOnClickListener {
            etSsid.setText("")
            etPassword.setText("")
            etSsid.requestFocus()
        }

        cardOptionQr.setOnClickListener {
            iniciarEscaneoQr()
        }

        btnEditarWifi.setOnClickListener {
            mostrarPaso(2)
        }

        btnAtras.setOnClickListener {
            if (pasoActual > 1) mostrarPaso(pasoActual - 1)
        }

        btnSiguiente.setOnClickListener {
            manejarSiguiente()
        }
    }

    private fun manejarSiguiente() {
        when (pasoActual) {
            1 -> {
                mostrarPaso(2)
            }
            2 -> {
                val ssid = etSsid.text.toString().trim()
                val password = etPassword.text.toString().trim()
                if (ssid.isEmpty()) {
                    etSsid.error = "Ingresa el nombre de la red"
                    return
                }
                currentSsidInput = ssid
                currentPasswordInput = password
                tvSelectedSsid.text = currentSsidInput
                tvResumenDispositivo.text =
                    if (connectedEspName.isNotBlank()) connectedEspName else "ESP32 Socket"
                mostrarPaso(3)
            }
            3 -> {
                enviarConfiguracionWifi()
            }
        }
    }

    private fun mostrarPaso(paso: Int) {
        pasoActual = paso

        stepContent1.visibility = if (paso == 1) View.VISIBLE else View.GONE
        stepContent2.visibility = if (paso == 2) View.VISIBLE else View.GONE
        stepContent3.visibility = if (paso == 3) View.VISIBLE else View.GONE

        actualizarStepIndicator(paso)

        btnAtras.visibility = if (paso == 1) View.INVISIBLE else View.VISIBLE

        when (paso) {
            1 -> {
                btnSiguiente.text = "Siguiente"
                btnSiguiente.setIconResource(R.drawable.arrow_right)
                btnSiguiente.icon?.let { btnSiguiente.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_END }
                btnSiguiente.isEnabled = isConnected
            }
            2 -> {
                btnSiguiente.text = "Siguiente"
                btnSiguiente.setIconResource(R.drawable.arrow_right)
                btnSiguiente.isEnabled = true
            }
            3 -> {
                btnSiguiente.text = "Enviar configuración"
                btnSiguiente.icon = null
                btnSiguiente.isEnabled = true
            }
        }
    }

    private fun actualizarStepIndicator(paso: Int) {
        fun aplicarEstado(dot: FrameLayout, label: TextView, numView: Int, activo: Boolean, completado: Boolean) {
            val numText = dot.findViewById<TextView>(numView)
            when {
                completado -> {
                    dot.setBackgroundResource(R.drawable.bg_circle_step_done)
                    numText.text = "✓"
                    numText.setTextColor(android.graphics.Color.WHITE)
                    label.setTextColor(ContextCompat.getColor(this, R.color.teal_primary))
                }
                activo -> {
                    dot.setBackgroundResource(R.drawable.bg_circle_step_active)
                    numText.text = if (numView == R.id.stepNum1) "1" else if (numView == R.id.stepNum2) "2" else "3"
                    numText.setTextColor(android.graphics.Color.WHITE)
                    label.setTextColor(ContextCompat.getColor(this, R.color.teal_primary))
                }
                else -> {
                    dot.setBackgroundResource(R.drawable.bg_circle_step_inactive)
                    numText.text = if (numView == R.id.stepNum1) "1" else if (numView == R.id.stepNum2) "2" else "3"
                    numText.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                    label.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                }
            }
        }

        aplicarEstado(stepDot1, stepLabel1, R.id.stepNum1, paso == 1, paso > 1)
        aplicarEstado(stepDot2, stepLabel2, R.id.stepNum2, paso == 2, paso > 2)
        aplicarEstado(stepDot3, stepLabel3, R.id.stepNum3, paso == 3, false)

        stepLine1.setBackgroundColor(
            ContextCompat.getColor(this, if (paso > 1) R.color.teal_primary else android.R.color.darker_gray)
        )
        stepLine2.setBackgroundColor(
            ContextCompat.getColor(this, if (paso > 2) R.color.teal_primary else android.R.color.darker_gray)
        )
    }

    private fun pedirPermisosLocationParaWifi() {
        requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
    }

    private fun autocompletarRedActual() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        val info = wifiManager.connectionInfo
        var ssid = info.ssid

        if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") {
            if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                ssid = ssid.substring(1, ssid.length - 1)
            }
            etSsid.setText(ssid)
            etPassword.setText("")
            etPassword.requestFocus()
        } else {
            mostrarSnackbar("No se detectó tu Wi-Fi actual. Ingrésalo manualmente.", true)
            etSsid.requestFocus()
        }
    }

    private fun guardarDispositivo(nombre: String, mac: String) {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putString(KEY_NAME, nombre)
            putString(KEY_MAC, mac)
            apply()
        }
    }

    private fun pedirPermisos() {
        val permisos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
        requestPermissionLauncher.launch(permisos)
    }

    private fun tienePermisosNecesarios(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    private fun verificarBluetoothEncendido() {
        if (!bluetoothAdapter.isEnabled) {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else {
            iniciarEscaneoBle()
        }
    }

    @SuppressLint("MissingPermission")
    private fun iniciarEscaneoBle() {
        if (!tienePermisosNecesarios()) {
            pedirPermisos()
            return
        }
        if (bluetoothLeScanner == null) {
            mostrarSnackbar("Escáner BLE no disponible en este dispositivo", true)
            return
        }

        discoveredDevices.clear()
        listAdapter.notifyDataSetChanged()
        tvSinDispositivos?.visibility = View.GONE

        progressScan?.visibility = View.VISIBLE
        isScanning = true

        bluetoothLeScanner?.startScan(scanCallback)

        handler.postDelayed({
            if (isScanning) {
                bluetoothLeScanner?.stopScan(scanCallback)
                detenerEscaneo()
            }
        }, SCAN_PERIOD)
    }

    @SuppressLint("MissingPermission")
    private fun detenerEscaneo() {
        bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
        progressScan?.visibility = View.INVISIBLE

        if (discoveredDevices.isEmpty()) {
            tvSinDispositivos?.visibility = View.VISIBLE
        }
    }

    private fun mostrarBottomSheetDispositivos() {
        if (bottomSheetDialog == null) {
            bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
            val view = layoutInflater.inflate(R.layout.bottom_sheet_devices, null)

            lvDispositivos = view.findViewById(R.id.lvDispositivos)
            progressScan = view.findViewById(R.id.progressScan)
            tvSinDispositivos = view.findViewById(R.id.tvSinDispositivos)

            lvDispositivos?.adapter = listAdapter

            lvDispositivos?.setOnItemClickListener { _, _, position, _ ->
                val device = discoveredDevices[position]
                bottomSheetDialog?.dismiss()
                conectarADispositivo(device)
            }

            bottomSheetDialog?.setContentView(view)
        }
        bottomSheetDialog?.show()
    }

    @SuppressLint("MissingPermission")
    private fun conectarADispositivo(device: BluetoothDevice) {
        detenerEscaneo()
        mostrarSnackbar("Conectando a ${device.name ?: "ESP32"}...", false)

        bluetoothGatt?.close()
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            runOnUiThread {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        isConnected = true
                        actualizarEstadoConexion(true)
                        mostrarSnackbar("ESP32 conectado", false)
                        gatt.requestMtu(512)
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        isConnected = false
                        actualizarEstadoConexion(false)

                        if (pasoActual == 3) {
                            runOnUiThread {
                                mostrarPaso(3)
                                ipRecibida = true
                                mostrarSnackbar("ESP32 configurado y reiniciando...", false)
                                finalizarConfiguracion(connectedEspIp)
                            }
                        } else {
                            if (pasoActual == 1) btnSiguiente.isEnabled = false
                            mostrarSnackbar("ESP32 desconectado", false)
                        }
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            gatt.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    wifiCharacteristic = service.getCharacteristic(WIFI_CHAR_UUID)
                    ipCharacteristic = service.getCharacteristic(IP_CHAR_UUID)

                    ipCharacteristic?.let { charac ->
                        gatt.setCharacteristicNotification(charac, true)
                        val descriptor = charac.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                        descriptor?.let { desc ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                gatt.writeDescriptor(desc, android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                            } else {
                                @Suppress("DEPRECATION")
                                desc.value = android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                @Suppress("DEPRECATION")
                                gatt.writeDescriptor(desc)
                            }
                        }
                        gatt.readCharacteristic(charac)
                    }

                    runOnUiThread {
                        @SuppressLint("MissingPermission")
                        val deviceName = gatt.device.name ?: "ESP32 Socket"
                        connectedEspName = deviceName
                        if (connectedEspMac.isBlank()) {
                            connectedEspMac = gatt.device.address
                        }
                        ipRecibida = false

                        tvSubEstadoConexion.text = "Conectado a $deviceName."
                        btnSiguiente.isEnabled = true
                        mostrarSnackbar("ESP32 listo. Continúa para configurar el Wi-Fi.", false)
                    }
                }
            } else {
                runOnUiThread {
                    mostrarSnackbar("Error al descubrir servicios del ESP32", true)
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (characteristic.uuid == IP_CHAR_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                @Suppress("DEPRECATION")
                val raw = characteristic.getStringValue(0)
                runOnUiThread {
                    procesarEstadoSocket(raw, finalizarSiCompleto = false)
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            runOnUiThread {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    mostrarSnackbar("Configuración enviada, esperando red...", false)
                } else {
                    mostrarSnackbar("Error al enviar la configuración", true)
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == IP_CHAR_UUID) {
                @Suppress("DEPRECATION")
                val raw = characteristic.getStringValue(0)

                runOnUiThread {
                    if (!ipRecibida || pasoActual == 3) {
                        ipRecibida = true
                        procesarEstadoSocket(raw, finalizarSiCompleto = (pasoActual == 3))
                    }
                }
            }
        }
    }

    private fun procesarEstadoSocket(raw: String?, finalizarSiCompleto: Boolean) {
        val (ip, mac) = parseEstadoSocket(raw)
        mac?.let { connectedEspMac = it }
        if (ip.isNotBlank() && ip != "0.0.0.0") {
            connectedEspIp = ip
            getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("saved_device_ip", ip)
                .apply()
        }
        if (finalizarSiCompleto) {
            mostrarSnackbar("¡Red asignada correctamente!", false)
            finalizarConfiguracion(connectedEspIp)
        }
    }

    private fun parseEstadoSocket(raw: String?): Pair<String, String?> {
        if (raw.isNullOrBlank()) return "" to null
        val parts = raw.split("|", limit = 2)
        val ip = parts[0].trim()
        val mac = parts.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        return ip to mac
    }

    @SuppressLint("MissingPermission")
    private fun enviarConfiguracionWifi() {
        val ssid = currentSsidInput
        val password = currentPasswordInput

        if (ssid.isEmpty()) {
            mostrarSnackbar("Configura una red Wi-Fi primero", true)
            return
        }
        if (!isConnected || bluetoothGatt == null) {
            mostrarSnackbar("Busca y conéctate a tu ESP32 primero", true)
            return
        }

        wifiCharacteristic?.let { characteristic ->
            val baseUrl = com.example.android.BuildConfig.BASE_URL
            val wsUrl = baseUrl.replace("http://", "ws://").replace("https://", "wss://")
            val token = getSharedPreferences("SesionApp", Context.MODE_PRIVATE).getString("apiToken", "") ?: ""
            val configData = "$ssid|$password|$wsUrl|$token"
            characteristic.value = configData.toByteArray()
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

            bluetoothGatt?.writeCharacteristic(characteristic)
            btnSiguiente.isEnabled = false
            mostrarSnackbar("Enviando configuración...", false)
        } ?: mostrarSnackbar("El ESP32 no expone la característica Wi-Fi necesaria", true)
    }

    private fun actualizarEstadoConexion(conectado: Boolean) {
        if (conectado) {
            cardEstado.setCardBackgroundColor(android.graphics.Color.parseColor("#E8F5E9"))
            iconEstadoBg.setBackgroundResource(R.drawable.bg_circle_green)
            ivEstadoConexion.setImageResource(R.drawable.wifi)
            ivEstadoConexion.setColorFilter(ContextCompat.getColor(this, R.color.teal_primary))

            tvEstadoConexion.text = "Conectado"
            tvEstadoConexion.setTextColor(ContextCompat.getColor(this, R.color.teal_primary))
            tvSubEstadoConexion.text = "Tu ESP32 está conectado."
        } else {
            cardEstado.setCardBackgroundColor(android.graphics.Color.parseColor("#FFF0F0"))
            iconEstadoBg.setBackgroundResource(R.drawable.bg_circle_red)
            ivEstadoConexion.setImageResource(R.drawable.wifi_off)
            ivEstadoConexion.setColorFilter(android.graphics.Color.parseColor("#F44336"))

            tvEstadoConexion.text = "No conectado"
            tvEstadoConexion.setTextColor(android.graphics.Color.parseColor("#F44336"))
            tvSubEstadoConexion.text = "Aún no se ha encontrado tu ESP32."
        }
    }

    private fun mostrarSnackbar(mensaje: String, esError: Boolean) {
        mainConfig?.let { view ->
            val snackbar = Snackbar.make(view, mensaje, Snackbar.LENGTH_SHORT)
            if (::bottomActionBar.isInitialized) {
                snackbar.setAnchorView(bottomActionBar)
            }
            if (esError) {
                snackbar.setBackgroundTint(android.graphics.Color.parseColor("#F44336"))
            }
            snackbar.show()
        }
    }

    private fun cargarAparatosLocales() {
        lifecycleScope.launch {
            aparatosLocales = withContext(Dispatchers.IO) {
                db.dispositivoDao().getAllDispositivosOnce()
            }
        }
    }

    private fun finalizarConfiguracion(ip: String) {
        if (connectedEspMac.isNotBlank()) {
            guardarDispositivo(connectedEspName, connectedEspMac)
        }

        lifecycleScope.launch {
            asegurarDispositivoRegistrado(ip)

            if (dispositivoGuardadoEnBd) {
                val mensaje = if (ip.isNotBlank()) {
                    "Dispositivo configurado y registrado (IP: $ip)"
                } else {
                    "Dispositivo configurado y registrado"
                }
                mostrarSnackbar(mensaje, false)
                setResult(RESULT_OK)
                finish()
            } else {
                mostrarSnackbar("No se pudo registrar el dispositivo en la nube", true)
            }
        }
    }

    private suspend fun asegurarDispositivoRegistrado(ip: String = "") {
        if (connectedEspMac.isBlank()) return

        val token = getSharedPreferences("SesionApp", Context.MODE_PRIVATE).getString("apiToken", "") ?: ""
        if (token.isBlank()) return

        registroMutex.withLock {
            if (dispositivoGuardadoEnBd) {
                aparatoIdRegistrado?.let { actualizarConfiguracionRedSync(it, connectedEspMac, ip, token) }
                return
            }

            val existente = withContext(Dispatchers.IO) {
                db.dispositivoDao().getAllDispositivosOnce()
                    .find { it.macBluetooth?.equals(connectedEspMac, ignoreCase = true) == true }
            }

            if (existente != null) {
                aparatoIdRegistrado = existente.id
                dispositivoGuardadoEnBd = true
                actualizarConfiguracionRedSync(existente.id, connectedEspMac, ip, token)
                return
            }

            val tipo = tipoDispositivo.ifBlank { "ESP32 Socket" }
            val nombre = connectedEspName.ifBlank { tipo }
            val metodoVinculacion = intent.getStringExtra("EXTRA_METODO_VINCULACION") ?: "ESP32"

            val habitacionDefault = withContext(Dispatchers.IO) {
                val casas = db.casaDao().getAllCasas().firstOrNull() ?: emptyList()
                val casaId = casas.firstOrNull()?.id ?: return@withContext null
                val habitaciones = db.habitacionDao().getHabitacionesByCasa(casaId).firstOrNull() ?: emptyList()
                habitaciones.firstOrNull()
            }

            val dispositivo = Dispositivo(
                id = 0,
                nombre = nombre,
                tipo = tipo,
                accion = null,
                comandoBluetooth = null,
                icono = iconoDispositivo,
                metodoVinculacion = metodoVinculacion,
                macBluetooth = connectedEspMac,
                nombreBluetooth = nombre,
                skHabitacionId = habitacionDefault?.id,
                nombreHabitacion = habitacionDefault?.nombre,
                fechaSincronizacion = null
            )

            var registrado = false
            ApiHandler.safeApiCall(
                activity = this@EspConfigActivity,
                showLoading = true,
                loadingTitle = "Registrando",
                loadingMessage = "Guardando dispositivo ESP32 en tu cuenta...",
                apiCall = { RetrofitClient.deviceService.createDispositivo("Bearer $token", dispositivo) },
                onSuccess = { response ->
                    val guardado = response.data
                    if (response.success && guardado != null) {
                        aparatoIdRegistrado = guardado.id
                        dispositivoGuardadoEnBd = true
                        registrado = true
                        withContext(Dispatchers.IO) {
                            db.dispositivoDao().insertDispositivo(guardado)
                        }
                        actualizarConfiguracionRedSync(guardado.id, connectedEspMac, ip, token)
                    }
                },
                onError = { error ->
                    mostrarSnackbar("Error al registrar dispositivo: $error", true)
                }
            )

            if (!registrado && !dispositivoGuardadoEnBd) {
                mostrarSnackbar("No se pudo registrar el dispositivo en la nube", true)
            }
        }
    }

    private suspend fun actualizarConfiguracionRedSync(
        aparatoId: Int,
        mac: String,
        ip: String,
        token: String
    ) {
        ApiHandler.safeApiCall(
            activity = this@EspConfigActivity,
            showLoading = false,
            apiCall = {
                val config = ConfiguracionRedRequest(
                    ipAddress = ip.ifBlank { "0.0.0.0" },
                    macAddress = mac,
                    hostName = connectedEspName.ifBlank { null },
                    deviceKey = mac,
                    puertoSocket = 81,
                    protocoloSocket = "ws",
                    rutaSocket = "/ws",
                    activo = true
                )
                RetrofitClient.deviceService.saveConfiguracionRed("Bearer $token", aparatoId, config)
            },
            onSuccess = { },
            onError = { }
        )
    }

    private fun iniciarEscaneoQr() {
        val options = com.journeyapps.barcodescanner.ScanOptions()
        options.setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
        options.setPrompt("Escanea el código QR de tu red Wi-Fi")
        options.setCameraId(0)
        options.setBeepEnabled(true)
        options.setBarcodeImageEnabled(false)
        options.setCaptureActivity(PortraitCaptureActivity::class.java)
        qrScanLauncher.launch(options)
    }

    private fun procesarContenidoQr(contenido: String) {
        if (contenido.startsWith("WIFI:")) {
            var ssid = ""
            var password = ""

            val partes = contenido.removePrefix("WIFI:").split(";")
            for (parte in partes) {
                if (parte.startsWith("S:")) {
                    ssid = parte.substring(2)
                } else if (parte.startsWith("P:")) {
                    password = parte.substring(2)
                }
            }

            if (ssid.isNotEmpty()) {
                etSsid.setText(ssid)
                etPassword.setText(password)
                mostrarSnackbar("Datos escaneados, revísalos antes de continuar", false)
            } else {
                mostrarSnackbar("El código QR no contiene un nombre de red válido", true)
            }
        } else {
            mostrarSnackbar("El código escaneado no es de una red Wi-Fi", true)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onDestroy() {
        super.onDestroy()
        if (hasBluetoothPermissions()) {
            @SuppressLint("MissingPermission")
            bluetoothGatt?.close()
        }
        bluetoothGatt = null
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onStop() {
        super.onStop()
        if (isScanning) {
            if (hasBluetoothPermissions()) {
                @SuppressLint("MissingPermission")
                bluetoothLeScanner?.stopScan(scanCallback)
            }
            isScanning = false
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }
}
