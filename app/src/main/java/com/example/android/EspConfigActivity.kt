package com.example.android

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.text.InputType
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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class EspConfigActivity : AppCompatActivity() {

    // Vistas principales
    private lateinit var cardEstado: MaterialCardView
    private lateinit var iconEstadoBg: FrameLayout
    private lateinit var ivEstadoConexion: ImageView
    private lateinit var tvEstadoConexion: TextView
    private lateinit var tvSubEstadoConexion: TextView

    private lateinit var tvSelectedSsid: TextView
    private lateinit var btnConfigurarWifi: MaterialButton
    private lateinit var btnEnviarConfig: MaterialButton
    private lateinit var cardBuscarDispositivos: MaterialCardView

    // Datos Wi-Fi actuales en memoria
    private var currentSsidInput = ""
    private var currentPasswordInput = ""

    // Bottom Sheets
    private var bottomSheetDialog: BottomSheetDialog? = null
    private var methodDialog: BottomSheetDialog? = null
    private var inputDialog: BottomSheetDialog? = null
    
    private var lvDispositivosBottom: ListView? = null
    private var progressScan: LinearProgressIndicator? = null

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
    // Flag para evitar que el diálogo de asociación se abra en bucle
    // (BLE puede disparar onCharacteristicChanged varias veces con la misma IP)
    private var dialogEspMostrado = false

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
    private var vistaRaiz: View? = null

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device

            if (!discoveredDevices.any { it.address == device.address }) {
                discoveredDevices.add(device)
                listAdapter.notifyDataSetChanged()
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
            mostrarSnackbar("Permisos necesarios denegados", true)
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            iniciarEscaneoBle()
        } else {
            mostrarSnackbar("Bluetooth necesario para conectar", false)
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

        // Inicializar BD y precargar aparatos para el diálogo de asociación
        db = AppDatabase.getDatabase(this)
        cargarAparatosLocales()
    }

    private fun inicializarVistas() {
        cardEstado = findViewById(R.id.cardEstado)
        iconEstadoBg = findViewById(R.id.iconEstadoBg)
        ivEstadoConexion = findViewById(R.id.ivEstadoConexion)
        tvEstadoConexion = findViewById(R.id.tvEstadoConexion)
        tvSubEstadoConexion = findViewById(R.id.tvSubEstadoConexion)

        tvSelectedSsid = findViewById(R.id.tvSelectedSsid)
        btnConfigurarWifi = findViewById(R.id.btnConfigurarWifi)
        btnEnviarConfig = findViewById(R.id.btnEnviarConfig)
        cardBuscarDispositivos = findViewById(R.id.cardBuscarDispositivos)

        listAdapter = DeviceAdapter(this, discoveredDevices)
    }

    private inner class DeviceAdapter(context: Context, private val devices: List<BluetoothDevice>) :
        ArrayAdapter<BluetoothDevice>(context, 0, devices) {

        @SuppressLint("MissingPermission")
        override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_bluetooth_device, parent, false)
            val device = getItem(position)

            val tvDeviceName = view.findViewById<TextView>(R.id.tvBtDeviceName)
            val tvDeviceMac = view.findViewById<TextView>(R.id.tvBtDeviceMac)

            tvDeviceName.text = device?.name ?: "Dispositivo Desconocido"
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
        btnConfigurarWifi.setOnClickListener {
            mostrarDialogoMetodo()
        }

        btnEnviarConfig.setOnClickListener {
            enviarConfiguracionWifi()
        }

        cardBuscarDispositivos.setOnClickListener {
            mostrarBottomSheetDispositivos()
        }

        findViewById<ImageView>(R.id.ivHelp).setOnClickListener {
            mostrarSnackbar("Para configurar tu cámara, debes estar cerca de ella y tener el Bluetooth encendido.", false)
        }
    }

    // --- Flujo de Dialogos ---

    private fun mostrarDialogoMetodo() {
        methodDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_wifi_method, null)
        methodDialog?.setContentView(view)

        val cardOptionWifi = view.findViewById<MaterialCardView>(R.id.cardOptionWifi)
        val cardOptionManual = view.findViewById<MaterialCardView>(R.id.cardOptionManual)
        val cardOptionQr = view.findViewById<MaterialCardView>(R.id.cardOptionQr)

        cardOptionWifi.setOnClickListener {
            methodDialog?.dismiss()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mostrarDialogoInputAutocompletado()
            } else {
                pedirPermisosLocationParaWifi()
            }
        }

        cardOptionManual.setOnClickListener {
            methodDialog?.dismiss()
            mostrarDialogoInput("", "")
        }

        cardOptionQr.setOnClickListener {
            methodDialog?.dismiss()
            iniciarEscaneoQr()
        }

        methodDialog?.show()
    }

    private fun pedirPermisosLocationParaWifi() {
        val permisos = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        requestPermissionLauncher.launch(permisos)
    }

    private fun mostrarDialogoInputAutocompletado() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        val info = wifiManager.connectionInfo
        var ssid = info.ssid

        if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") {
            if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                ssid = ssid.substring(1, ssid.length - 1)
            }
            mostrarDialogoInput(ssid, "")
        } else {
            mostrarSnackbar("No se obtuvo el Wi-Fi actual. Ingresa manualmente.", true)
            mostrarDialogoInput("", "")
        }
    }

    private fun mostrarDialogoInput(ssid: String, password: String) {
        inputDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_wifi_input, null)
        inputDialog?.setContentView(view)

        val etSsid = view.findViewById<TextInputEditText>(R.id.etDialogSsid)
        val etPassword = view.findViewById<TextInputEditText>(R.id.etDialogPassword)
        val cbMostrarContrasena = view.findViewById<MaterialCheckBox>(R.id.cbDialogMostrarContrasena)
        val btnDialogGuardar = view.findViewById<MaterialButton>(R.id.btnDialogGuardar)

        etSsid.setText(ssid)
        etPassword.setText(password)

        if (ssid.isNotEmpty()) {
            etPassword.requestFocus()
        } else {
            etSsid.requestFocus()
        }

        cbMostrarContrasena.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            etPassword.setSelection(etPassword.text?.length ?: 0)
        }

        btnDialogGuardar.setOnClickListener {
            val nuevoSsid = etSsid.text.toString().trim()
            val nuevaPassword = etPassword.text.toString().trim()

            if (nuevoSsid.isEmpty()) {
                etSsid.error = "Ingresa el nombre de la red"
                return@setOnClickListener
            }

            currentSsidInput = nuevoSsid
            currentPasswordInput = nuevaPassword
            actualizarUiWifiSeleccionado()
            inputDialog?.dismiss()
        }

        inputDialog?.show()
    }

    private fun actualizarUiWifiSeleccionado() {
        if (currentSsidInput.isNotEmpty()) {
            tvSelectedSsid.text = currentSsidInput
            btnEnviarConfig.isEnabled = true
        } else {
            tvSelectedSsid.text = "Ninguna red seleccionada"
            btnEnviarConfig.isEnabled = false
        }
    }

    private fun mostrarBottomSheetDispositivos() {
        bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_scan_devices, null)
        bottomSheetDialog?.setContentView(view)

        lvDispositivosBottom = view.findViewById(R.id.lvDispositivosBottom)
        progressScan = view.findViewById(R.id.progressScan)

        lvDispositivosBottom?.adapter = listAdapter

        lvDispositivosBottom?.setOnItemClickListener { _, _, position, _ ->
            val device = discoveredDevices[position]
            conectarADispositivo(device)
            bottomSheetDialog?.dismiss()
        }

        bottomSheetDialog?.setOnDismissListener {
            if (isScanning) detenerEscaneo()
        }

        bottomSheetDialog?.show()
        
        if (tienePermisosNecesarios()) {
            verificarBluetoothEncendido()
        } else {
            pedirPermisos()
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
            mostrarSnackbar("Escáner BLE no disponible", true)
            return
        }

        discoveredDevices.clear()
        listAdapter.notifyDataSetChanged()

        mostrarProgresoScan(true)
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
        mostrarProgresoScan(false)

        if (discoveredDevices.isEmpty()) {
            mostrarSnackbar("No se encontraron dispositivos", false)
        }
    }

    private fun mostrarProgresoScan(mostrar: Boolean) {
        progressScan?.visibility = if (mostrar) View.VISIBLE else View.INVISIBLE
    }

    // --- Conexión GATT ---
    @SuppressLint("MissingPermission")
    private fun conectarADispositivo(device: BluetoothDevice) {
        detenerEscaneo()
        mostrarSnackbar("Conectando a ${device.name}...", false)

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
                        mostrarSnackbar("Conectado al ESP32", false)
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        isConnected = false
                        actualizarEstadoConexion(false)
                        mostrarSnackbar("Dispositivo desconectado", false)
                    }
                }
            }
        }

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
                            desc.value = android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(desc)
                        }
                    }

                    runOnUiThread {
                        @SuppressLint("MissingPermission")
                        val deviceName = gatt.device.name ?: "ESP32"
                        connectedEspMac = gatt.device.address
                        connectedEspName = deviceName
                        dialogEspMostrado = false  // reset al conectar un nuevo dispositivo
                        guardarDispositivo(deviceName, gatt.device.address)

                        tvSubEstadoConexion.text = "Conectado a $deviceName."
                        mostrarSnackbar("Servicios descubiertos. Listo para configurar.", false)
                    }
                }
            } else {
                runOnUiThread {
                    mostrarSnackbar("Error al descubrir servicios", true)
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
                    // Eliminado el finish() para esperar la IP
                } else {
                    mostrarSnackbar("Error al enviar configuración", true)
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

                // Guardar la IP en las preferencias globales
                val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString("saved_device_ip", ip).apply()

                runOnUiThread {
                    // Evitar que el diálogo se repita si BLE notifica la IP más de una vez
                    if (!dialogEspMostrado) {
                        dialogEspMostrado = true
                        mostrarSnackbar("¡IP recibida: $ip! Asociando al aparato...", false)
                        mostrarDialogoAsociarEsp(ip)
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
            mostrarSnackbar("Busca y conéctate a un ESP32 primero", true)
            return
        }

        wifiCharacteristic?.let { characteristic ->
            val configData = "$ssid|$password"
            characteristic.value = configData.toByteArray()
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

            bluetoothGatt?.writeCharacteristic(characteristic)
            mostrarSnackbar("Enviando configuración...", false)
        } ?: mostrarSnackbar("Característica WiFi no disponible", true)
    }

    // --- UI Helpers ---
    private fun actualizarEstadoConexion(conectado: Boolean) {
        if (conectado) {
            cardEstado.setCardBackgroundColor(android.graphics.Color.parseColor("#E8F5E9"))
            iconEstadoBg.setBackgroundResource(R.drawable.bg_circle_green)
            iconEstadoBg.backgroundTintList = null
            ivEstadoConexion.setImageResource(R.drawable.wifi)
            ivEstadoConexion.setColorFilter(ContextCompat.getColor(this, R.color.teal_primary))
            
            tvEstadoConexion.text = "Conectado"
            tvEstadoConexion.setTextColor(ContextCompat.getColor(this, R.color.teal_primary))
            tvSubEstadoConexion.text = "Tu dispositivo ESP32 está conectado."
        } else {
            cardEstado.setCardBackgroundColor(android.graphics.Color.parseColor("#FFF0F0"))
            iconEstadoBg.setBackgroundResource(R.drawable.bg_circle_red)
            iconEstadoBg.backgroundTintList = null
            ivEstadoConexion.setImageResource(R.drawable.wifi_off)
            ivEstadoConexion.setColorFilter(android.graphics.Color.parseColor("#F44336"))
            
            tvEstadoConexion.text = "No conectado"
            tvEstadoConexion.setTextColor(android.graphics.Color.parseColor("#F44336"))
            tvSubEstadoConexion.text = "Tu dispositivo ESP32 no está conectado."
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

    // --- Configuración de Red ESP32 ---

    private fun cargarAparatosLocales() {
        lifecycleScope.launch {
            aparatosLocales = withContext(Dispatchers.IO) {
                db.dispositivoDao().getAllDispositivosOnce()
            }
        }
    }

    private fun mostrarDialogoAsociarEsp(ip: String) {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_esp_vinculado, null)
        sheet.setContentView(view)
        sheet.setCancelable(false)

        // Mostrar datos del ESP32
        view.findViewById<TextView>(R.id.tvEspIp).text = ip
        view.findViewById<TextView>(R.id.tvEspMac).text =
            connectedEspMac.ifBlank { "N/D" }

        // Poblar spinner con aparatos disponibles
        val spinnerAparato = view.findViewById<MaterialAutoCompleteTextView>(R.id.spinnerAparato)
        val nombresAparatos = aparatosLocales.map { it.nombre ?: "Aparato ${it.id}" }
        val adapterSpinner = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, nombresAparatos)
        spinnerAparato.setAdapter(adapterSpinner)
        if (nombresAparatos.isNotEmpty()) {
            spinnerAparato.setText(nombresAparatos[0], false)
        }

        // Botón guardar
        view.findViewById<MaterialButton>(R.id.btnGuardarConfig).setOnClickListener {
            if (aparatosLocales.isEmpty()) {
                mostrarSnackbar("No hay aparatos registrados. Agrega uno primero.", true)
                return@setOnClickListener
            }
            val textoSeleccionado = spinnerAparato.text.toString().trim()
            val idx = nombresAparatos.indexOf(textoSeleccionado)
            if (idx >= 0) {
                sheet.dismiss()
                guardarConfiguracionRed(aparatosLocales[idx].id, ip)
            } else {
                mostrarSnackbar("Selecciona un aparato de la lista", true)
            }
        }

        // Botón saltar
        view.findViewById<MaterialButton>(R.id.btnSaltarConfig).setOnClickListener {
            sheet.dismiss()
            navegarACamara(ip)
        }

        sheet.show()
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
                    // Navegar igualmente para no bloquear al usuario
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

    // --- Ciclo de Vida ---
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
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
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

    private fun iniciarEscaneoQr() {
        val options = com.journeyapps.barcodescanner.ScanOptions()
        options.setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
        options.setPrompt("Escanea el código QR del Módem Wi-Fi")
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
                mostrarDialogoInput(ssid, password)
                mostrarSnackbar("Datos escaneados, revísalos y guárdalos", false)
            } else {
                mostrarSnackbar("El código QR no contiene un nombre de red válido", true)
            }
        } else {
            mostrarSnackbar("El código escaneado no es de una red Wi-Fi", true)
        }
    }
}