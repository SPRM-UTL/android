package com.example.android

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
import com.example.android.db.AppDatabase
import com.example.android.db.Dispositivo
import com.example.android.network.ApiHandler
import com.example.android.network.ConfiguracionRedRequest
import com.example.android.network.RetrofitClient
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class EspConfigActivity : AppCompatActivity() {

    // --- Header / step indicator ---
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

    // --- Step 1: buscar dispositivo ---
    private lateinit var cardEstado: MaterialCardView
    private lateinit var iconEstadoBg: FrameLayout
    private lateinit var ivEstadoConexion: ImageView
    private lateinit var tvEstadoConexion: TextView
    private lateinit var tvSubEstadoConexion: TextView
    private lateinit var btnBuscarDispositivos: MaterialButton
    private lateinit var contenedorListaDispositivos: LinearLayout
    private lateinit var lvDispositivos: ListView
    private lateinit var progressScan: LinearProgressIndicator
    private lateinit var tvSinDispositivos: TextView

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
    private lateinit var btnAtras: MaterialButton
    private lateinit var btnSiguiente: MaterialButton

    private lateinit var btnBack: ImageView
    private lateinit var ivHelp: ImageView

    private var vistaRaiz: View? = null

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
    // Aparato local seleccionado para asociar (índice en aparatosLocales)
    private var aparatoSeleccionadoIdx: Int = -1
    // Flag para evitar que la pantalla de confirmación se dispare en bucle
    // (BLE puede disparar onCharacteristicChanged varias veces con la misma IP)
    private var ipRecibida = false

    // Flag para evitar que el diálogo de asociación se abra en bucle
    private var dialogEspMostrado = false

    // Modo "Add Device" para aparatos genéricos (ej. Enchufe)
    private var isAddDeviceMode = false
    private var tipoDispositivo: String = ""
    private var iconoDispositivo: String? = null

    // Constantes
    companion object {
        private val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        private val WIFI_CHAR_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a9")
        private val IP_CHAR_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26aa")
        private const val SCAN_PERIOD: Long = 10000
        private const val PREF_NAME = "EspConfigPrefs"
        private const val KEY_MAC = "saved_mac_address"
        private const val KEY_NAME = "saved_device_name"
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
                tvSinDispositivos.visibility = View.GONE
            }
        }

        override fun onScanFailed(errorCode: Int) {
            runOnUiThread {
                detenerEscaneo()
                mostrarSnackbar("Error en escaneo: $errorCode", true)
            }
        }
    }

    // Permisos
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            verificarBluetoothEncendido()
        } else {
            mostrarSnackbar("Activa los permisos de Bluetooth y ubicación para buscar tu cámara", true)
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            iniciarEscaneoBle()
        } else {
            mostrarSnackbar("Necesitas Bluetooth activado para conectar tu cámara", false)
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
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = true

        setContentView(R.layout.activity_esp_config)
        vistaRaiz = findViewById(android.R.id.content)

        ViewCompat.setOnApplyWindowInsetsListener(vistaRaiz!!) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        inicializarVistas()
        inicializarBluetooth()
        configurarListeners()
        mostrarPaso(1)

        isAddDeviceMode = intent.getBooleanExtra("EXTRA_MODE_ADD_DEVICE", false)
        tipoDispositivo = intent.getStringExtra("EXTRA_TIPO_DISPOSITIVO") ?: ""
        iconoDispositivo = intent.getStringExtra("EXTRA_ICONO_DISPOSITIVO")

        db = AppDatabase.getDatabase(this)
        cargarAparatosLocales()
    }

    private fun inicializarVistas() {
        // Header
        btnBack = findViewById(R.id.btnBack)
        ivHelp = findViewById(R.id.ivHelp)

        // Step indicator
        stepDot1 = findViewById(R.id.stepDot1)
        stepDot2 = findViewById(R.id.stepDot2)
        stepDot3 = findViewById(R.id.stepDot3)
        stepLine1 = findViewById(R.id.stepLine1)
        stepLine2 = findViewById(R.id.stepLine2)
        stepLabel1 = findViewById(R.id.stepLabel1)
        stepLabel2 = findViewById(R.id.stepLabel2)
        stepLabel3 = findViewById(R.id.stepLabel3)

        // Step content
        stepContent1 = findViewById(R.id.stepContent1)
        stepContent2 = findViewById(R.id.stepContent2)
        stepContent3 = findViewById(R.id.stepContent3)

        // Step 1
        cardEstado = findViewById(R.id.cardEstado)
        iconEstadoBg = findViewById(R.id.iconEstadoBg)
        ivEstadoConexion = findViewById(R.id.ivEstadoConexion)
        tvEstadoConexion = findViewById(R.id.tvEstadoConexion)
        tvSubEstadoConexion = findViewById(R.id.tvSubEstadoConexion)
        btnBuscarDispositivos = findViewById(R.id.btnBuscarDispositivos)
        contenedorListaDispositivos = findViewById(R.id.contenedorListaDispositivos)
        lvDispositivos = findViewById(R.id.lvDispositivos)
        progressScan = findViewById(R.id.progressScan)
        tvSinDispositivos = findViewById(R.id.tvSinDispositivos)

        listAdapter = DeviceAdapter(this, discoveredDevices)
        lvDispositivos.adapter = listAdapter

        // Step 2
        cardOptionWifi = findViewById(R.id.cardOptionWifi)
        cardOptionManual = findViewById(R.id.cardOptionManual)
        cardOptionQr = findViewById(R.id.cardOptionQr)
        etSsid = findViewById(R.id.etSsid)
        etPassword = findViewById(R.id.etPassword)

        // Step 3
        tvResumenDispositivo = findViewById(R.id.tvResumenDispositivo)
        cardSeleccionWifi = findViewById(R.id.cardSeleccionWifi)
        tvSelectedSsid = findViewById(R.id.tvSelectedSsid)
        btnEditarWifi = findViewById(R.id.btnEditarWifi)

        // Bottom bar
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

            tvDeviceName.text = device?.name ?: "Dispositivo desconocido"
            tvDeviceMac.text = device?.address ?: "00:00:00:00:00:00"

            return view
        }
    }

    private fun inicializarBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
    }

    private fun configurarListeners() {
        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        ivHelp.setOnClickListener {
            mostrarSnackbar("Acerca tu teléfono a la cámara y mantén el Bluetooth encendido durante todo el proceso.", false)
        }

        btnBuscarDispositivos.setOnClickListener {
            contenedorListaDispositivos.visibility = View.VISIBLE
            if (tienePermisosNecesarios()) {
                verificarBluetoothEncendido()
            } else {
                pedirPermisos()
            }
        }

        lvDispositivos.setOnItemClickListener { _, _, position, _ ->
            val device = discoveredDevices[position]
            conectarADispositivo(device)
        }

        // Step 2: métodos de carga de Wi-Fi
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

        // Step 3
        btnEditarWifi.setOnClickListener {
            mostrarPaso(2)
        }

        // Navegación del wizard
        btnAtras.setOnClickListener {
            if (pasoActual > 1) mostrarPaso(pasoActual - 1)
        }

        btnSiguiente.setOnClickListener {
            manejarSiguiente()
        }
    }

    // --- Navegación del wizard ---

    private fun manejarSiguiente() {
        when (pasoActual) {
            1 -> {
                // Solo se llega aquí si ya hay un ESP32 conectado (botón habilitado)
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
                    if (connectedEspName.isNotBlank()) connectedEspName else "Cámara ESP32"
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
                    numText.setTextColor(android.graphics.Color.WHITE)
                    label.setTextColor(ContextCompat.getColor(this, R.color.teal_primary))
                }
                else -> {
                    dot.setBackgroundResource(R.drawable.bg_circle_step_inactive)
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

    // --- Permisos ---
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

    // --- Escaneo BLE ---
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
        tvSinDispositivos.visibility = View.GONE

        progressScan.visibility = View.VISIBLE
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
        progressScan.visibility = View.INVISIBLE

        if (discoveredDevices.isEmpty()) {
            tvSinDispositivos.visibility = View.VISIBLE
        }
    }

    // --- Conexión GATT ---
    @SuppressLint("MissingPermission")
    private fun conectarADispositivo(device: BluetoothDevice) {
        detenerEscaneo()
        mostrarSnackbar("Conectando a ${device.name ?: "tu cámara"}...", false)

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
                        mostrarSnackbar("Cámara conectada", false)
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        isConnected = false
                        actualizarEstadoConexion(false)
                        if (pasoActual == 1) btnSiguiente.isEnabled = false
                        mostrarSnackbar("Cámara desconectada", false)
                    }
                }
            }
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
                                // API 33+: writeDescriptor(descriptor, value) es el reemplazo;
                                // BluetoothGattDescriptor.value ya no tiene setter público.
                                gatt.writeDescriptor(desc, android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                            } else {
                                @Suppress("DEPRECATION")
                                desc.value = android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                @Suppress("DEPRECATION")
                                gatt.writeDescriptor(desc)
                            }
                        }
                    }

                    runOnUiThread {
                        @SuppressLint("MissingPermission")
                        val deviceName = gatt.device.name ?: "Cámara ESP32"
                        connectedEspMac = gatt.device.address
                        connectedEspName = deviceName
                        ipRecibida = false
                        guardarDispositivo(deviceName, gatt.device.address)

                        tvSubEstadoConexion.text = "Conectado a $deviceName."
                        btnSiguiente.isEnabled = true
                        mostrarSnackbar("Cámara lista. Continúa para configurar el Wi-Fi.", false)
                    }
                }
            } else {
                runOnUiThread {
                    mostrarSnackbar("Error al descubrir servicios de la cámara", true)
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
                val ip = characteristic.getStringValue(0)

                val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString("saved_device_ip", ip).apply()

                runOnUiThread {
                    // Evitar repetir el flujo de asociación si BLE notifica la IP más de una vez
                    if (!ipRecibida) {
                        ipRecibida = true
                        connectedEspIp = ip
                        mostrarSnackbar("¡Red asignada correctamente!", false)
                        mostrarSeleccionDeAparato(ip)
                    }
                }
            }
        }
    }

    // --- Operaciones BLE ---
    @SuppressLint("MissingPermission")
    private fun enviarConfiguracionWifi() {
        val ssid = currentSsidInput
        val password = currentPasswordInput

        if (ssid.isEmpty()) {
            mostrarSnackbar("Configura una red Wi-Fi primero", true)
            return
        }
        if (!isConnected || bluetoothGatt == null) {
            mostrarSnackbar("Busca y conéctate a tu cámara primero", true)
            return
        }

        wifiCharacteristic?.let { characteristic ->
            val configData = "$ssid|$password"
            characteristic.value = configData.toByteArray()
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

            bluetoothGatt?.writeCharacteristic(characteristic)
            btnSiguiente.isEnabled = false
            mostrarSnackbar("Enviando configuración...", false)
        } ?: mostrarSnackbar("La cámara no expone la característica Wi-Fi necesaria", true)
    }

    // --- UI Helpers ---
    private fun actualizarEstadoConexion(conectado: Boolean) {
        if (conectado) {
            cardEstado.setCardBackgroundColor(android.graphics.Color.parseColor("#E8F5E9"))
            iconEstadoBg.setBackgroundResource(R.drawable.bg_circle_green)
            ivEstadoConexion.setImageResource(R.drawable.wifi)
            ivEstadoConexion.setColorFilter(ContextCompat.getColor(this, R.color.teal_primary))

            tvEstadoConexion.text = "Conectado"
            tvEstadoConexion.setTextColor(ContextCompat.getColor(this, R.color.teal_primary))
            tvSubEstadoConexion.text = "Tu cámara está conectada."
        } else {
            cardEstado.setCardBackgroundColor(android.graphics.Color.parseColor("#FFF0F0"))
            iconEstadoBg.setBackgroundResource(R.drawable.bg_circle_red)
            ivEstadoConexion.setImageResource(R.drawable.wifi_off)
            ivEstadoConexion.setColorFilter(android.graphics.Color.parseColor("#F44336"))

            tvEstadoConexion.text = "No conectado"
            tvEstadoConexion.setTextColor(android.graphics.Color.parseColor("#F44336"))
            tvSubEstadoConexion.text = "Aún no se ha encontrado tu cámara."
        }
    }

    private fun mostrarSnackbar(mensaje: String, esError: Boolean) {
        vistaRaiz?.let { view ->
            val snackbar = Snackbar.make(view, mensaje, Snackbar.LENGTH_SHORT)
            if (esError) {
                snackbar.setBackgroundTint(android.graphics.Color.parseColor("#F44336"))
            }
            snackbar.show()
        }
    }

    // --- Asociación con aparato local + guardado de red ---

    private fun cargarAparatosLocales() {
        lifecycleScope.launch {
            aparatosLocales = withContext(Dispatchers.IO) {
                db.dispositivoDao().getAllDispositivosOnce()
            }
        }
    }

    /**
     * Tras recibir la IP del ESP32, se asocia automáticamente al primer aparato local
     * disponible (o navega directo si no hay aparatos registrados).
     */
    private fun mostrarSeleccionDeAparato(ip: String) {
        if (isAddDeviceMode) {
            mostrarDialogoNombrarYGuardar(ip)
            return
        }
        if (aparatosLocales.isEmpty()) {
            navegarACamara(ip)
            return
        }
        aparatoSeleccionadoIdx = 0
        guardarConfiguracionRed(aparatosLocales[0].id, ip)
    }

    private fun mostrarDialogoNombrarYGuardar(ip: String) {
        val input = com.google.android.material.textfield.TextInputEditText(this)
        input.hint = "Ej. $tipoDispositivo Sala"

        val layout = android.widget.FrameLayout(this)
        val padding = (20 * resources.displayMetrics.density).toInt()
        layout.setPadding(padding, padding, padding, padding)
        layout.addView(input)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Nombrar dispositivo")
            .setMessage("Tu $tipoDispositivo se conectó a WiFi (IP: $ip). ¿Qué nombre le vas a poner?")
            .setView(layout)
            .setCancelable(false)
            .setPositiveButton("Guardar") { dialog, _ ->
                val nombre = input.text.toString().trim()
                if (nombre.isNotEmpty()) {
                    guardarDispositivoNuevo(nombre, ip)
                } else {
                    mostrarSnackbar("El nombre no puede estar vacío", true)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Omitir") { dialog, _ -> 
                dialog.dismiss()
                navegarACamara(ip) 
            }
            .show()
    }

    private fun guardarDispositivoNuevo(nombre: String, ip: String) {
        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val token = sharedPref.getString("apiToken", "") ?: return

        val dispositivo = com.example.android.db.Dispositivo(
            id = 0,
            nombre = nombre,
            tipo = tipoDispositivo,
            accion = null,
            comandoBluetooth = null,
            icono = iconoDispositivo,
            macBluetooth = "VIRTUAL-${System.currentTimeMillis()}",
            nombreBluetooth = null,
            fechaSincronizacion = null
        )

        lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = this@EspConfigActivity,
                showLoading = true,
                loadingTitle = "Guardando",
                loadingMessage = "Registrando dispositivo en tu cuenta...",
                apiCall = { RetrofitClient.deviceService.createDispositivo("Bearer $token", dispositivo) },
                onSuccess = { response ->
                    val nuevoDispositivo = response.data
                    if (nuevoDispositivo != null) {
                        guardarConfiguracionRed(nuevoDispositivo.id, ip)
                    } else {
                        mostrarSnackbar("Error: El servidor no devolvió el dispositivo creado", true)
                        navegarACamara(ip)
                    }
                },
                onError = { error ->
                    mostrarSnackbar("Error al crear dispositivo: $error", true)
                    navegarACamara(ip)
                }
            )
        }
    }

    /** Llama al endpoint PUT /api/Dim_Aparatos/{id}/configuracion-red */
    private fun guardarConfiguracionRed(aparatoId: Int, ip: String) {
        lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = this@EspConfigActivity,
                showLoading = true,
                loadingTitle = "Guardando",
                loadingMessage = "Guardando configuración de red...",
                apiCall = {
                    val token = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
                        .getString("apiToken", "") ?: ""
                    val config = ConfiguracionRedRequest(
                        ipAddress = ip,
                        macAddress = connectedEspMac.ifBlank { null },
                        hostName = connectedEspName.ifBlank { null },
                        deviceKey = if (connectedEspMac.isNotBlank()) "${connectedEspMac}_${aparatoId}" else null,
                        puertoSocket = 81,
                        protocoloSocket = "ws",
                        rutaSocket = "/ws",
                        activo = true
                    )
                    RetrofitClient.deviceService.saveConfiguracionRed("Bearer $token", aparatoId, config)
                },
                onSuccess = {
                    mostrarSnackbar("Configuración guardada correctamente", false)
                    navegarACamara(ip)
                },
                onError = { errorMsg ->
                    mostrarSnackbar("Error al guardar: $errorMsg", true)
                    navegarACamara(ip)
                }
            )
        }
    }

    private fun navegarACamara(ip: String) {
        val intent = Intent(this, DeviceCameraActivity::class.java)
        intent.putExtra("EXTRA_IP", ip)
        startActivity(intent)
        finish()
    }

    // --- QR ---
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

    // --- Ciclo de vida ---
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