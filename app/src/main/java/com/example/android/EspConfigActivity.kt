package com.example.android

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import java.util.*

class EspConfigActivity : AppCompatActivity() {

    private lateinit var ivEstadoConexion: ImageView
    private lateinit var tvEstadoConexion: TextView
    private lateinit var layoutDispositivo: LinearLayout
    private lateinit var tvNombreDispositivo: TextView
    private lateinit var cardIp: MaterialCardView
    private lateinit var tvIpAddress: TextView
    private lateinit var layoutSsid: LinearLayout
    private lateinit var tvSsidConectado: TextView
    private lateinit var tvRssi: TextView
    private lateinit var btnCopiarIp: MaterialButton
    private lateinit var btnCompartirWifi: MaterialButton
    private lateinit var btnEscanearQr: MaterialButton
    private lateinit var etSsid: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnEnviarConfig: MaterialButton
    private lateinit var btnEscanear: MaterialButton
    private lateinit var progressCargando: LinearProgressIndicator
    private lateinit var tvTituloDispositivos: TextView
    private lateinit var lvDispositivos: ListView
    private lateinit var fabRefresh: FloatingActionButton

    // Bluetooth
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var wifiCharacteristic: BluetoothGattCharacteristic? = null
    private var ipCharacteristic: BluetoothGattCharacteristic? = null

    // Listas
    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private val deviceNames = mutableListOf<String>()
    private lateinit var listAdapter: ArrayAdapter<String>

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
    private var currentIp = ""
    private var currentSsid = ""
    private var vistaRaiz: View? = null

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: "Dispositivo Desconocido"

            if (!discoveredDevices.any { it.address == device.address }) {
                discoveredDevices.add(device)
                deviceNames.add("$deviceName\n${device.address}")
                listAdapter.notifyDataSetChanged()
                mostrarListaDispositivos(true)
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
        cargarDispositivoGuardado()
    }

    private fun inicializarVistas() {
        ivEstadoConexion = findViewById(R.id.ivEstadoConexion)
        tvEstadoConexion = findViewById(R.id.tvEstadoConexion)
        layoutDispositivo = findViewById(R.id.layoutDispositivo)
        tvNombreDispositivo = findViewById(R.id.tvNombreDispositivo)
        cardIp = findViewById(R.id.cardIp)
        tvIpAddress = findViewById(R.id.tvIpAddress)
        layoutSsid = findViewById(R.id.layoutSsid)
        tvSsidConectado = findViewById(R.id.tvSsidConectado)
        tvRssi = findViewById(R.id.tvRssi)
        btnCopiarIp = findViewById(R.id.btnCopiarIp)
        btnCompartirWifi = findViewById(R.id.btnCompartirWifi)
        btnEscanearQr = findViewById(R.id.btnEscanearQr)
        etSsid = findViewById(R.id.etSsid)
        etPassword = findViewById(R.id.etPassword)
        btnEnviarConfig = findViewById(R.id.btnEnviarConfig)
        btnEscanear = findViewById(R.id.btnEscanear)
        progressCargando = findViewById(R.id.progressCargando)
        tvTituloDispositivos = findViewById(R.id.tvTituloDispositivos)
        lvDispositivos = findViewById(R.id.lvDispositivos)
        fabRefresh = findViewById(R.id.fabRefresh)

        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceNames)
        lvDispositivos.adapter = listAdapter
    }

    private fun inicializarBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
    }

    private fun configurarListeners() {
        btnEscanear.setOnClickListener {
            if (tienePermisosNecesarios()) {
                verificarBluetoothEncendido()
            } else {
                pedirPermisos()
            }
        }

        btnEnviarConfig.setOnClickListener {
            enviarConfiguracionWifi()
        }

        btnCopiarIp.setOnClickListener {
            copiarIpAlPortapapeles()
        }

        btnCompartirWifi.setOnClickListener {
            autocompletarWifiActual()
        }

        btnEscanearQr.setOnClickListener {
            iniciarEscaneoQr()
        }

        fabRefresh.setOnClickListener {
            if (isConnected) {
                leerIpDelDispositivo()
            } else {
                iniciarEscaneoBle()
            }
        }

        lvDispositivos.setOnItemClickListener { _, _, position, _ ->
            val device = discoveredDevices[position]
            conectarADispositivo(device)
        }
    }

    private fun cargarDispositivoGuardado() {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val nombreGuardado = prefs.getString(KEY_NAME, null)
        if (nombreGuardado != null) {
            tvNombreDispositivo.text = nombreGuardado
            layoutDispositivo.visibility = View.VISIBLE
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
        deviceNames.clear()
        listAdapter.notifyDataSetChanged()

        mostrarProgreso(true)
        btnEscanear.isEnabled = false
        btnEscanear.text = "Escaneando..."
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
        mostrarProgreso(false)
        btnEscanear.isEnabled = true
        btnEscanear.text = "Escanear Dispositivos"

        if (discoveredDevices.isEmpty()) {
            mostrarSnackbar("No se encontraron dispositivos", false)
        }
    }

    // --- Conexión GATT ---
    @SuppressLint("MissingPermission")
    private fun conectarADispositivo(device: BluetoothDevice) {
        detenerEscaneo()
        mostrarProgreso(true)
        btnEscanear.isEnabled = false
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
                        ocultarInfoIp()
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

                    runOnUiThread {
                        mostrarProgreso(false)
                        btnEscanear.isEnabled = true
                        btnEscanear.text = "Escanear Dispositivos"
                        ocultarListaDispositivos()

                        @SuppressLint("MissingPermission")
                        val deviceName = gatt.device.name ?: "ESP32"
                        guardarDispositivo(deviceName, gatt.device.address)
                        tvNombreDispositivo.text = deviceName
                        layoutDispositivo.visibility = View.VISIBLE

                        mostrarSnackbar("Servicios descubiertos", false)
                        leerIpDelDispositivo()
                    }
                }
            } else {
                runOnUiThread {
                    mostrarSnackbar("Error al descubrir servicios", true)
                    mostrarProgreso(false)
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (characteristic.uuid) {
                    IP_CHAR_UUID -> {
                        val ip = String(value)
                        runOnUiThread {
                            actualizarInfoIp(ip)
                        }
                    }
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
                    mostrarSnackbar("Configuración enviada correctamente", false)
                    // Leer IP después de enviar configuración
                    handler.postDelayed({
                        leerIpDelDispositivo()
                    }, 2000)
                } else {
                    mostrarSnackbar("Error al enviar configuración", true)
                }
            }
        }
    }

    // --- Operaciones BLE ---
    @SuppressLint("MissingPermission")
    private fun leerIpDelDispositivo() {
        ipCharacteristic?.let { characteristic ->
            bluetoothGatt?.readCharacteristic(characteristic)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enviarConfiguracionWifi() {
        val ssid = etSsid.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (ssid.isEmpty()) {
            etSsid.error = "Ingrese el nombre de la red"
            return
        }

        if (!isConnected || bluetoothGatt == null) {
            mostrarSnackbar("Conéctese a un dispositivo primero", true)
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
            ivEstadoConexion.setColorFilter(ContextCompat.getColor(this, R.color.teal_primary))
            tvEstadoConexion.text = "Conectado"
            tvEstadoConexion.setTextColor(ContextCompat.getColor(this, R.color.teal_primary))
            cardIp.visibility = View.VISIBLE
        } else {
            ivEstadoConexion.setColorFilter(android.graphics.Color.parseColor("#F44336"))
            tvEstadoConexion.text = "Desconectado"
            tvEstadoConexion.setTextColor(android.graphics.Color.parseColor("#F44336"))
            cardIp.visibility = View.GONE
        }
    }

    private fun actualizarInfoIp(ip: String) {
        currentIp = ip
        tvIpAddress.text = ip
        cardIp.visibility = View.VISIBLE
    }

    private fun ocultarInfoIp() {
        currentIp = ""
        currentSsid = ""
        tvIpAddress.text = "---"
        tvSsidConectado.text = "---"
        layoutSsid.visibility = View.GONE
        tvRssi.visibility = View.GONE
        cardIp.visibility = View.GONE
    }

    private fun mostrarListaDispositivos(mostrar: Boolean) {
        tvTituloDispositivos.visibility = if (mostrar) View.VISIBLE else View.GONE
        lvDispositivos.visibility = if (mostrar) View.VISIBLE else View.GONE
    }

    private fun ocultarListaDispositivos() {
        mostrarListaDispositivos(false)
    }

    private fun mostrarProgreso(mostrar: Boolean) {
        progressCargando.visibility = if (mostrar) View.VISIBLE else View.INVISIBLE
    }

    private fun copiarIpAlPortapapeles() {
        if (currentIp.isNotEmpty()) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("IP ESP32", currentIp)
            clipboard.setPrimaryClip(clip)
            mostrarSnackbar("IP copiada: $currentIp", false)
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

    private fun autocompletarWifiActual() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            obtenerSsidYMostrar()
        } else {
            pedirPermisos()
        }
    }

    private fun obtenerSsidYMostrar() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        val info = wifiManager.connectionInfo
        var ssid = info.ssid

        if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") {
            if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                ssid = ssid.substring(1, ssid.length - 1)
            }
            etSsid.setText(ssid)
            mostrarSnackbar("SSID auto-completado. Ingrese su contraseña.", false)
            etPassword.requestFocus()
        } else {
            mostrarSnackbar("No se obtuvo el Wi-Fi. Revise estar conectado y tener el GPS encendido.", true)
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
                etSsid.setText(ssid)
                etPassword.setText(password)
                mostrarSnackbar("Datos cargados correctamente desde el QR", false)
            } else {
                mostrarSnackbar("El código QR no contiene un nombre de red válido", true)
            }
        } else {
            mostrarSnackbar("El código escaneado no es de una red Wi-Fi", true)
        }
    }
}