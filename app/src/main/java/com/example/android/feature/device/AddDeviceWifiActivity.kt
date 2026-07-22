package com.example.android.feature.device
import com.example.android.core.ui.adapters.WifiDeviceAdapter
import com.example.android.core.network.client.ConfiguracionRedRequest
import com.example.android.core.db.models.AparatoTipo

import com.example.android.R

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
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
import com.example.android.core.network.wifi.DeviceConnectionManager
import com.example.android.core.network.client.SocketClient
import com.example.android.core.network.wifi.WifiScanManager
import com.example.android.core.db.init.AppDatabase
import com.example.android.core.db.models.Dispositivo
import com.example.android.core.network.api.ApiHandler
import com.example.android.core.network.client.RetrofitClient
import android.graphics.drawable.ColorDrawable
import android.widget.ArrayAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Color
import android.text.InputType

class AddDeviceWifiActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILTRO_TIPO = "FILTRO_TIPO"
        const val EXTRA_FILTRO_ICONO = "FILTRO_ICONO"
        const val EXTRA_FILTRO_PALABRAS = "FILTRO_PALABRAS"
    }

    private lateinit var progressCargando: LinearProgressIndicator
    private lateinit var rvDispositivos: RecyclerView
    private lateinit var shimmerViewContainer: com.facebook.shimmer.ShimmerFrameLayout
    private lateinit var headerSubtitle: TextView

    private lateinit var gestorWifi: WifiScanManager
    private lateinit var connectionManager: DeviceConnectionManager
    private lateinit var socketClient: SocketClient
    private lateinit var listAdapter: WifiDeviceAdapter

    private var methodDialog: BottomSheetDialog? = null
    private var inputDialog: BottomSheetDialog? = null
    private var currentDispositivoAProvisionar: ScanResult? = null

    private lateinit var db: AppDatabase
    private var tiposDisponibles: List<com.example.android.core.db.models.AparatoTipo> = emptyList()
    private var filtroTipo: String? = null
    private var filtroIcono: String? = null
    private var filtroPalabras: String? = null
    private var dispositivoDescubiertoIp: String? = null
    private var dispositivoDescubiertoMac: String? = null

    private val qrScanLauncher = registerForActivityResult(
        com.journeyapps.barcodescanner.ScanContract()
    ) { result ->
        if (result.contents != null) {
            procesarContenidoQr(result.contents)
        } else {
            Toast.makeText(this, "Escaneo cancelado", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permisos ->
            val todosConcedidos = permisos.entries.all { it.value }
            if (todosConcedidos) {
                verificarGPSYScan()
            } else {
                Toast.makeText(this, "Permisos de ubicación/Wi-Fi son necesarios", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContentView(R.layout.activity_add_device_wifi)

        db = AppDatabase.getDatabase(this)
        filtroTipo = intent.getStringExtra(EXTRA_FILTRO_TIPO)
        filtroIcono = intent.getStringExtra(EXTRA_FILTRO_ICONO)
        filtroPalabras = intent.getStringExtra(EXTRA_FILTRO_PALABRAS)
        cargarTiposDesdeServidor()

        configurarInsets()
        configurarUI()

        gestorWifi = WifiScanManager(
            this,
            onWifiDeviceFound = { result ->
                val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    result.wifiSsid?.toString() ?: result.SSID
                } else {
                    result.SSID
                }
                val cleanSsid = ssid?.removePrefix("\"")?.removeSuffix("\"")
                if (!cleanSsid.isNullOrEmpty() && cleanSsid.startsWith("LEDnet")) {
                    runOnUiThread {
                        if (shimmerViewContainer.visibility == View.VISIBLE) {
                            shimmerViewContainer.stopShimmer()
                            shimmerViewContainer.visibility = View.GONE
                            rvDispositivos.visibility = View.VISIBLE
                        }
                        listAdapter.agregarDispositivo(result)
                        headerSubtitle.text = "Activado • ${listAdapter.itemCount} redes"
                    }
                }
            },
            onLog = { /* log if needed */ }
        )

        connectionManager = DeviceConnectionManager(this) { msg ->
            // log to verify connection
        }

        socketClient = SocketClient { msg -> }

        pedirPermisos()
    }

    private fun configurarInsets() {
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
    }

    private fun configurarUI() {
        progressCargando = findViewById(R.id.progressCargando)
        rvDispositivos = findViewById(R.id.rvDispositivosWifi)
        shimmerViewContainer = findViewById(R.id.shimmerViewContainer)
        headerSubtitle = findViewById(R.id.tvHeaderSubtitle)

        listAdapter = WifiDeviceAdapter { dispositivo ->
            currentDispositivoAProvisionar = dispositivo
            mostrarDialogoMetodo()
        }
        rvDispositivos.layoutManager = LinearLayoutManager(this)
        rvDispositivos.adapter = listAdapter

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        findViewById<View>(R.id.fabRefresh).setOnClickListener {
            pedirPermisos()
        }
    }

    private fun pedirPermisos() {
        val permisos = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permisos.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        val faltantes = permisos.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (faltantes.isNotEmpty()) {
            requestPermissionLauncher.launch(faltantes.toTypedArray())
        } else {
            verificarGPSYScan()
        }
    }

    private fun verificarGPSYScan() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        if (!locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
            val builder = android.app.AlertDialog.Builder(this)
            builder.setTitle("Ubicación desactivada")
            builder.setMessage("Es necesario activar la ubicación para detectar redes Wi-Fi.")
            builder.setPositiveButton("Configuración") { _, _ ->
                startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            builder.setNegativeButton("Cancelar", null)
            builder.show()
        } else {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            if (!wifiManager.isWifiEnabled) {
                Toast.makeText(this, "Por favor enciende el Wi-Fi para buscar redes", Toast.LENGTH_LONG).show()
            } else {
                iniciarEscaneo()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun iniciarEscaneo() {
        listAdapter.limpiar()
        headerSubtitle.text = "Buscando redes cercanas..."
        progressCargando.visibility = View.VISIBLE
        rvDispositivos.visibility = View.GONE
        shimmerViewContainer.visibility = View.VISIBLE
        shimmerViewContainer.startShimmer()

        gestorWifi.startWifiScan()

        window.decorView.postDelayed({
            if (!isDestroyed && !isFinishing) {
                progressCargando.visibility = View.INVISIBLE
                if (shimmerViewContainer.visibility == View.VISIBLE) {
                    shimmerViewContainer.stopShimmer()
                    shimmerViewContainer.visibility = View.GONE
                    rvDispositivos.visibility = View.VISIBLE
                }
            }
        }, 10000)
    }

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
                Toast.makeText(this, "Permiso de ubicación necesario para leer el Wi-Fi actual", Toast.LENGTH_SHORT).show()
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

    private fun mostrarDialogoInputAutocompletado() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        val info = wifiManager.connectionInfo
        var ssid = info.ssid

        if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") {
            if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                ssid = ssid.substring(1, ssid.length - 1)
            }
            val prefs = getSharedPreferences("SmartSocketPrefs", Context.MODE_PRIVATE)
            val savedPass = prefs.getString("HOME_PASSWORD", "") ?: ""
            val suggestedPass = if (prefs.getString("HOME_SSID", "") == ssid) savedPass else ""
            mostrarDialogoInput(ssid, suggestedPass)
        } else {
            Toast.makeText(this, "No se obtuvo el Wi-Fi actual. Ingresa manualmente.", Toast.LENGTH_LONG).show()
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

            val prefs = getSharedPreferences("SmartSocketPrefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("HOME_SSID", nuevoSsid)
                .putString("HOME_PASSWORD", nuevaPassword)
                .apply()

            inputDialog?.dismiss()

            currentDispositivoAProvisionar?.let { dispositivo ->
                val apSsidStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    dispositivo.wifiSsid?.toString() ?: dispositivo.SSID
                } else {
                    dispositivo.SSID
                }
                val apSsidLimpio = apSsidStr?.removePrefix("\"")?.removeSuffix("\"") ?: ""

                ejecutarVinculacionWifi(apSsidLimpio, nuevoSsid, nuevaPassword)
            }
        }

        inputDialog?.show()
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
                Toast.makeText(this, "Datos escaneados, revísalos y guárdalos", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "El código QR no contiene un nombre de red válido", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "El código escaneado no es de una red Wi-Fi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ejecutarVinculacionWifi(apSsid: String, homeSsid: String, homePass: String) {
        headerSubtitle.text = "Conectando y vinculando..."
        progressCargando.visibility = View.VISIBLE

        connectionManager.connectToWifiAp(apSsid)

        lifecycleScope.launch(Dispatchers.IO) {
            delay(5000)

            socketClient.provisionMagicHome("10.10.123.3", homeSsid, homePass)
            socketClient.provisionMagicHome("192.168.4.1", homeSsid, homePass)

            connectionManager.disconnectWifi()

            // Esperar a que el dispositivo se una a la red doméstica
            // El socket LEDnet puede tardar 10-20s en conectarse; intentamos hasta 3 veces
            delay(8000)
            var intentos = 0
            var coincidencia: Pair<String, String>? = null
            val bssidObjetivo = currentDispositivoAProvisionar?.BSSID?.uppercase()

            while (intentos < 3 && coincidencia == null) {
                intentos++
                android.util.Log.d("AddDeviceWifiActivity", "Scan post-provisioning: intento $intentos/3")
                val dispositivosEnRed = socketClient.scanLocalNetworkSuspend()
                coincidencia = dispositivosEnRed.find { (_, mac) ->
                    !bssidObjetivo.isNullOrEmpty() && mac.uppercase() == bssidObjetivo
                } ?: dispositivosEnRed.firstOrNull()
                if (coincidencia == null && intentos < 3) delay(3000)
            }

            dispositivoDescubiertoIp  = coincidencia?.first
            dispositivoDescubiertoMac = coincidencia?.second ?: bssidObjetivo

            withContext(Dispatchers.Main) {
                progressCargando.visibility = View.INVISIBLE
                if (dispositivoDescubiertoIp != null) {
                    Toast.makeText(
                        this@AddDeviceWifiActivity,
                        "Dispositivo detectado en ${dispositivoDescubiertoIp}",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this@AddDeviceWifiActivity,
                        "Credenciales enviadas. No se detectó IP. Puedes ingresarla manualmente.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                mostrarAlertaConfiguracion(currentDispositivoAProvisionar)
            }
        }
    }

    private fun cargarTiposDesdeServidor() {
        val sharedPreferences = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("apiToken", null)

        if (!token.isNullOrEmpty()) {
            lifecycleScope.launch {
                ApiHandler.safeApiCall(
                    activity = this@AddDeviceWifiActivity,
                    showLoading = false,
                    loadingTitle = "",
                    loadingMessage = "",
                    apiCall = { RetrofitClient.deviceService.getTiposAparato("Bearer $token") },
                    onSuccess = { apiResponse ->
                        if (apiResponse.success && apiResponse.data != null) {
                            tiposDisponibles = apiResponse.data
                        }
                    },
                    onError = {}
                )
            }
        }
    }

    /**
     * Detecta automáticamente el tipo de dispositivo a partir del nombre/SSID/clase
     * Bluetooth-Wi-Fi anunciado por el hardware (p. ej. "LEDnet-Bulb-AABBCC",
     * "LEDnet-Strip-XYZ", "LEDnet-Speaker-001").
     *
     * Devuelve el nombre de tipo tal como existe en [tiposDisponibles] cuando hay
     * coincidencia con el catálogo del servidor; si no, cae en una lista local de
     * tipos conocidos; si tampoco coincide, devuelve "General".
     */
    private fun detectarTipoPorNombre(nombreCrudo: String?): String {
        val nombre = (nombreCrudo ?: "").uppercase()

        // Palabras clave -> tipo. Se evalúan en orden, la primera coincidencia gana.
        val reglas = listOf(
            listOf("MULTISOCKET", "MULTI SOCKET", "REGLETA", "POWERSTRIP", "POWER STRIP", "MULTIENCHUFE", "CONTACTOS") to "MultiSocket",
            listOf("BULB", "FOCO", "LIGHT", "LAMP", "STRIP", "LED") to "Focos",
            listOf("SPEAKER", "BOCINA", "AUDIO", "SOUND") to "Bocinas",
            listOf("FAN", "VENTILADOR") to "Ventilador Inteligente",
            listOf("TV", "TELEVISION", "TELEVISIÓN", "TELEVISOR") to "Televisión",
            listOf("HEADPHONE", "AUDIFONO", "AUDÍFONO", "EARBUD") to "Audífonos",
            listOf("PLUG", "ENCHUFE", "SOCKET", "OUTLET") to "Enchufe",
            listOf("CAM", "CAMERA", "CAMARA", "CÁMARA") to "Cámara"
        )

        for ((claves, tipo) in reglas) {
            if (claves.any { nombre.contains(it) }) {
                // Si el catálogo del servidor tiene un tipo equivalente, se prioriza
                // ese nombre exacto para mantener consistencia con el backend.
                val coincidenciaServidor = tiposDisponibles.find {
                    it.nombreTipo.equals(tipo, ignoreCase = true)
                }
                return coincidenciaServidor?.nombreTipo ?: tipo
            }
        }

        return "General"
    }

    private fun actualizarIconoTipo(tipo: String, ivTipoIcono: ImageView) {
        val tipoObj = tiposDisponibles.find { it.nombreTipo == tipo }
        val iconName = tipoObj?.icono ?: "ic_default"

        // Tamaño fijo para todos los tipos (consistente con el layout: 48dp).
        val tamanoFijoPx = (48 * resources.displayMetrics.density).toInt()
        val params = ivTipoIcono.layoutParams
        params.width = tamanoFijoPx
        params.height = tamanoFijoPx
        ivTipoIcono.layoutParams = params

        val cleanIconName = iconName.substringBeforeLast(".").replace("-", "_")
        val resId = resources.getIdentifier(cleanIconName, "drawable", packageName)
        if (resId != 0 && cleanIconName != "ic_default") {
            ivTipoIcono.setImageResource(resId)
            ivTipoIcono.visibility = View.VISIBLE
        } else {
        val iconRes = when (tipo) {
            "Audífonos"  -> R.drawable.headphones
            "Bocinas"    -> R.drawable.speaker
            "Focos"      -> R.drawable.lightbulb
            "Luces"      -> R.drawable.lamp_floor
            "Ventilador Inteligente" -> R.drawable.wind
            "Televisión" -> R.drawable.tv_minimal
            "Enchufe", "MultiSocket", "Sockets Inteligentes" -> R.drawable.plug
            "Cámara", "Cámaras" -> R.drawable.camera
            "Asistente"  -> R.drawable.ic_input_add
            else         -> R.drawable.circle_question
        }
            ivTipoIcono.setImageResource(iconRes)
            ivTipoIcono.visibility = View.VISIBLE
        }
    }

    private fun mostrarAlertaConfiguracion(scanResult: ScanResult?) {
        val themedContext = android.view.ContextThemeWrapper(this, com.google.android.material.R.style.Theme_Material3_Light_Dialog)
        val dialogView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_config_device, null)

        val dialog = MaterialAlertDialogBuilder(themedContext)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTarget = dialogView.findViewById<TextView>(R.id.tvDeviceTarget)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etDeviceName)
        val layoutTipo = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutTipo)
        val etType = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.etDeviceType)
        val etCasa = dialogView.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.etCasa)
        val etHabitacion = dialogView.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.etHabitacion)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnConfirm = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirm)
        val btnDelete = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDeleteDialog)
        val ivTipoIcono = dialogView.findViewById<android.widget.ImageView>(R.id.ivTipoIcono)

        var localSelectedCasaId: Int? = null
        var localSelectedHabitacionId: Int? = null

        lifecycleScope.launch {
            val casas = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { db.casaDao().getAllCasas().firstOrNull() ?: emptyList() }
            if (casas.isEmpty()) return@launch

            val nombresCasas = casas.map { it.nombre }
            val adapterCasas = android.widget.ArrayAdapter(themedContext, android.R.layout.simple_spinner_dropdown_item, nombresCasas)
            etCasa.setAdapter(adapterCasas)

            val casaInicial = casas.first()
            localSelectedCasaId = casaInicial.id
            etCasa.setText(casaInicial.nombre, false)

            fun cargarHabitaciones(casaId: Int) {
                lifecycleScope.launch {
                    val habitaciones = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { db.habitacionDao().getHabitacionesByCasa(casaId).firstOrNull() ?: emptyList() }
                    val nombresHabitaciones = habitaciones.map { it.nombre }
                    val adapterHabitaciones = android.widget.ArrayAdapter(themedContext, android.R.layout.simple_spinner_dropdown_item, nombresHabitaciones)
                    etHabitacion.setAdapter(adapterHabitaciones)

                    if (habitaciones.isNotEmpty()) {
                        val habInicial = habitaciones.first()
                        localSelectedHabitacionId = habInicial.id
                        etHabitacion.setText(habInicial.nombre, false)
                    } else {
                        localSelectedHabitacionId = null
                        etHabitacion.setText("", false)
                    }

                    etHabitacion.setOnItemClickListener { _, _, position, _ ->
                        localSelectedHabitacionId = habitaciones[position].id
                    }
                }
            }

            cargarHabitaciones(localSelectedCasaId!!)

            etCasa.setOnItemClickListener { _, _, position, _ ->
                val selectedCasa = casas[position]
                if (localSelectedCasaId != selectedCasa.id) {
                    localSelectedCasaId = selectedCasa.id
                    localSelectedHabitacionId = null
                    cargarHabitaciones(selectedCasa.id)
                }
            }
        }

        val ssidStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            scanResult?.wifiSsid?.toString() ?: scanResult?.SSID
        } else {
            scanResult?.SSID
        }
        val ssidLimpio = ssidStr?.removePrefix("\"")?.removeSuffix("\"") ?: "Dispositivo Wi-Fi"

        tvTarget.text = ssidLimpio
        etName.setText(ssidLimpio)

        // --- Tipo: priorizar el seleccionado en SelectTypeDevice, luego detección automática ---
        val tipoDetectado = filtroTipo?.takeIf { it.isNotBlank() }
            ?: detectarTipoPorNombre(ssidLimpio)
        etType.setText(tipoDetectado, false)
        actualizarIconoTipo(tipoDetectado, ivTipoIcono)

        // El campo de tipo queda oculto/bloqueado: el valor ya se asignó solo.
        layoutTipo.visibility = View.GONE
        etType.isEnabled = false

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnDelete.visibility = View.GONE

        btnConfirm.setOnClickListener {
            val finalName = etName.text.toString().trim()
            val finalType = etType.text.toString().trim()

            if (finalName.isEmpty()) {
                etName.error = "Ingresa un nombre"
                return@setOnClickListener
            }

            if (localSelectedHabitacionId == null) {
                etHabitacion.error = "Selecciona una habitación"
                return@setOnClickListener
            }

            dialog.dismiss()

            val userId = getSharedPreferences("SesionApp", Context.MODE_PRIVATE).getInt("userId", 0)

            val macDispositivo = dispositivoDescubiertoMac
                ?: scanResult?.BSSID
                ?: ""

            val nuevo = Dispositivo(
                id = 0,
                nombre = finalName,
                skHabitacionId = localSelectedHabitacionId,
                nombreHabitacion = etHabitacion.text.toString(),
                tipo = finalType.ifBlank { "General" },
                accion = "Encendido",
                comandoBluetooth = "BT_ON",
                icono = filtroIcono ?: "ic_default",
                metodoVinculacion = "WIFI",
                macBluetooth = macDispositivo,
                nombreBluetooth = ssidLimpio,
                ipAddress = dispositivoDescubiertoIp,
                fechaSincronizacion = java.time.LocalDateTime.now().toString()
            )

            guardarEnServidor(nuevo, macDispositivo, dispositivoDescubiertoIp)
        }

        dialog.show()
    }

    private fun guardarEnServidor(dispositivo: Dispositivo, mac: String, ip: String?) {
        lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = this@AddDeviceWifiActivity,
                showLoading = true,
                loadingTitle = "Guardando",
                loadingMessage = "Sincronizando dispositivo...",
                apiCall = {
                    val token = getSharedPreferences("SesionApp", Context.MODE_PRIVATE).getString("apiToken", "") ?: ""
                    RetrofitClient.deviceService.createDispositivo("Bearer $token", dispositivo)
                },
                onSuccess = { response ->
                    val guardado = response.data
                    if (response.success && guardado != null) {
                        withContext(Dispatchers.IO) {
                            db.dispositivoDao().insertDispositivo(guardado)
                        }
                        if (!ip.isNullOrBlank() && mac.isNotBlank()) {
                            guardarConfiguracionRed(guardado.id, mac, ip)
                        } else {
                            Toast.makeText(this@AddDeviceWifiActivity, "Guardado exitoso", Toast.LENGTH_SHORT).show()
                            setResult(RESULT_OK)
                            finish()
                        }
                    } else {
                        Toast.makeText(
                            this@AddDeviceWifiActivity,
                            "El servidor no devolvió el dispositivo creado",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                onError = { errorMsg ->
                    Toast.makeText(this@AddDeviceWifiActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private suspend fun guardarConfiguracionRed(aparatoId: Int, mac: String, ip: String) {
        val token = getSharedPreferences("SesionApp", Context.MODE_PRIVATE).getString("apiToken", "") ?: ""
        ApiHandler.safeApiCall(
            activity = this@AddDeviceWifiActivity,
            showLoading = false,
            apiCall = {
                val config = com.example.android.core.network.client.ConfiguracionRedRequest(
                    ipAddress = ip,
                    macAddress = mac,
                    hostName = null,
                    deviceKey = mac,
                    puertoSocket = 5577,
                    protocoloSocket = "tcp",
                    rutaSocket = null,
                    activo = true
                )
                RetrofitClient.deviceService.saveConfiguracionRed("Bearer $token", aparatoId, config)
            },
            onSuccess = {
                Toast.makeText(this@AddDeviceWifiActivity, "Dispositivo sincronizado en la red", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            },
            onError = { errorMsg ->
                Toast.makeText(
                    this@AddDeviceWifiActivity,
                    "Guardado parcial: $errorMsg",
                    Toast.LENGTH_LONG
                ).show()
                setResult(RESULT_OK)
                finish()
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        gestorWifi.stopWifiScan()
        connectionManager.disconnectWifi()
    }
}
