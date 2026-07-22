package com.example.android.feature.device
import com.example.android.core.ui.adapters.BluetoothDeviceAdapter
import com.example.android.core.db.models.AparatoTipo

import com.example.android.R

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.util.Log
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
import com.example.android.core.network.bluetooth.BluetoothScanManager
import com.example.android.core.network.bluetooth.ResultadoDispositivoBt
import com.example.android.core.db.init.AppDatabase
import com.example.android.core.db.models.Dispositivo
import com.example.android.core.network.api.ApiHandler
import com.example.android.core.network.client.RetrofitClient
import android.graphics.drawable.ColorDrawable
import android.graphics.Color
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddDeviceBluetoothActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILTRO_TIPO = "FILTRO_TIPO"
        const val EXTRA_FILTRO_ICONO = "FILTRO_ICONO"
        const val EXTRA_FILTRO_PALABRAS = "FILTRO_PALABRAS"
    }

    private lateinit var progressCargando: LinearProgressIndicator
    private lateinit var rvDispositivos: RecyclerView
    private lateinit var shimmerViewContainer: com.facebook.shimmer.ShimmerFrameLayout
    private lateinit var headerSubtitle: TextView

    private lateinit var gestorBluetooth: BluetoothScanManager
    private lateinit var listAdapter: BluetoothDeviceAdapter

    private var currentDispositivoAProvisionar: ResultadoDispositivoBt? = null

    private lateinit var db: AppDatabase
    private var tiposDisponibles: List<com.example.android.core.db.models.AparatoTipo> = emptyList()
    private var filtroTipo: String? = null
    private var filtroIcono: String? = null
    private var filtroPalabras: String? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permisos ->
            val todosConcedidos = permisos.entries.all { it.value }
            if (todosConcedidos) {
                verificarGPSYScan()
            } else {
                Toast.makeText(this, "Permisos de ubicación/Bluetooth son necesarios", Toast.LENGTH_LONG).show()
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
        setContentView(R.layout.activity_add_device_bluetooth)

        db = AppDatabase.getDatabase(this)
        filtroTipo = intent.getStringExtra(EXTRA_FILTRO_TIPO)
        filtroIcono = intent.getStringExtra(EXTRA_FILTRO_ICONO)
        filtroPalabras = intent.getStringExtra(EXTRA_FILTRO_PALABRAS)
        cargarTiposDesdeServidor()

        configurarInsets()
        configurarUI()

        gestorBluetooth = BluetoothScanManager(this)

        pedirPermisos()
    }

    private fun configurarInsets() {
        val root = findViewById<View>(R.id.mainAddDeviceBluetooth)
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
        rvDispositivos = findViewById(R.id.rvDispositivosBluetooth)
        shimmerViewContainer = findViewById(R.id.shimmerViewContainer)
        headerSubtitle = findViewById(R.id.tvHeaderSubtitle)

        listAdapter = BluetoothDeviceAdapter { dispositivo ->
            currentDispositivoAProvisionar = dispositivo
            mostrarAlertaConfiguracion(dispositivo)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permisos.add(Manifest.permission.BLUETOOTH_SCAN)
            permisos.add(Manifest.permission.BLUETOOTH_CONNECT)
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
        if (!gestorBluetooth.isGpsEnabled()) {
            val builder = android.app.AlertDialog.Builder(this)
            builder.setTitle("Ubicación desactivada")
            builder.setMessage("Es necesario activar la ubicación para detectar dispositivos Bluetooth.")
            builder.setPositiveButton("Configuración") { _, _ ->
                startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            builder.setNegativeButton("Cancelar", null)
            builder.show()
        } else if (!gestorBluetooth.bluetoothActivado) {
            Toast.makeText(this, "Por favor enciende el Bluetooth para buscar dispositivos", Toast.LENGTH_LONG).show()
        } else {
            iniciarEscaneo()
        }
    }

    @SuppressLint("MissingPermission")
    private fun iniciarEscaneo() {
        Log.d("AddDeviceBluetooth", "iniciarEscaneo: Comenzando escaneo de dispositivos Bluetooth")
        listAdapter.limpiar()
        headerSubtitle.text = "Buscando dispositivos cercanos..."
        progressCargando.visibility = View.VISIBLE
        rvDispositivos.visibility = View.GONE
        shimmerViewContainer.visibility = View.VISIBLE
        shimmerViewContainer.startShimmer()

        gestorBluetooth.iniciarEscaneo(
            alEncontrarDispositivo = { dispositivo ->
                Log.d("AddDeviceBluetooth", "iniciarEscaneo: Dispositivo encontrado -> MAC: ${dispositivo.mac}, Nombre: ${dispositivo.nombre}")
                runOnUiThread {
                    if (shimmerViewContainer.visibility == View.VISIBLE) {
                        shimmerViewContainer.stopShimmer()
                        shimmerViewContainer.visibility = View.GONE
                        rvDispositivos.visibility = View.VISIBLE
                    }
                    listAdapter.agregarDispositivo(dispositivo)
                    headerSubtitle.text = "Activado • ${listAdapter.itemCount} dispositivos"
                }
            },
            alFinalizarEscaneo = {
                Log.d("AddDeviceBluetooth", "iniciarEscaneo: Escaneo finalizado")
                runOnUiThread {
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

    private fun cargarTiposDesdeServidor() {
        val sharedPreferences = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("apiToken", null)

        if (!token.isNullOrEmpty()) {
            lifecycleScope.launch {
                ApiHandler.safeApiCall(
                    activity = this@AddDeviceBluetoothActivity,
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

    private fun detectarTipoPorNombre(nombreCrudo: String?): String {
        val nombre = (nombreCrudo ?: "").uppercase()
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

    private fun mostrarAlertaConfiguracion(dispositivoBt: ResultadoDispositivoBt) {
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

        tvTarget.text = dispositivoBt.nombre
        etName.setText(dispositivoBt.nombre)

        val tipoDetectado = filtroTipo?.takeIf { it.isNotBlank() }
            ?: detectarTipoPorNombre(dispositivoBt.nombre)
        etType.setText(tipoDetectado, false)
        actualizarIconoTipo(tipoDetectado, ivTipoIcono)

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

            val nuevo = Dispositivo(
                id = 0,
                nombre = finalName,
                skHabitacionId = localSelectedHabitacionId,
                nombreHabitacion = etHabitacion.text.toString(),
                tipo = finalType.ifBlank { "General" },
                accion = "Encendido",
                comandoBluetooth = "BT_ON", // O el valor real configurado
                icono = filtroIcono ?: "ic_default",
                metodoVinculacion = "BLUETOOTH",
                macBluetooth = dispositivoBt.mac,
                nombreBluetooth = dispositivoBt.nombre,
                ipAddress = null,
                fechaSincronizacion = java.time.LocalDateTime.now().toString()
            )

            guardarEnServidor(nuevo)
        }

        dialog.show()
    }

    private fun guardarEnServidor(dispositivo: Dispositivo) {
        Log.d("AddDeviceBluetooth", "guardarEnServidor: Enviando dispositivo a la API: nombre=${dispositivo.nombre}, mac=${dispositivo.macBluetooth}")
        lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = this@AddDeviceBluetoothActivity,
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
                        Log.d("AddDeviceBluetooth", "guardarEnServidor: Dispositivo guardado exitosamente en API con ID=${guardado.id}")
                        withContext(Dispatchers.IO) {
                            db.dispositivoDao().insertDispositivo(guardado)
                        }
                        Toast.makeText(this@AddDeviceBluetoothActivity, "Guardado exitoso", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        Log.w("AddDeviceBluetooth", "guardarEnServidor: Servidor no devolvió el dispositivo creado. Success=${response.success}")
                        Toast.makeText(
                            this@AddDeviceBluetoothActivity,
                            "El servidor no devolvió el dispositivo creado",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                onError = { errorMsg ->
                    Log.e("AddDeviceBluetooth", "guardarEnServidor: Error en la llamada a la API: $errorMsg")
                    Toast.makeText(this@AddDeviceBluetoothActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gestorBluetooth.detenerEscaneo()
    }
}
