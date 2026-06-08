package com.example.android

import android.annotation.SuppressLint
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.Window
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

    private lateinit var tvDispositivoGuardado: TextView
    private lateinit var tvEstadoConexion: TextView
    private lateinit var viewStatusDot: View
    private lateinit var ivIconoEstado: ImageView
    
    private lateinit var progressCargando: LinearProgressIndicator
    private lateinit var rvDispositivos: RecyclerView

    private lateinit var gestorBluetooth: BluetoothScanManager
    private lateinit var listAdapter: BluetoothDeviceAdapter
    private lateinit var db: AppDatabase

    private val PREF_NAME = "BluetoothPrefs"
    private val KEY_MAC = "saved_mac_address"
    private val KEY_NAME = "saved_device_name"

    private var editDeviceId: Int = -1
    private var existingDevice: Dispositivo? = null

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.entries.all { it.value }) {
            verificarYEncenderBluetooth()
        } else {
            Snackbars.error(findViewById(android.R.id.content), "Permisos necesarios denegados", Toast.LENGTH_LONG).show()
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) verificarGPSYScan()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Estilo de barra de navegación blanca con iconos oscuros
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
        val rv = findViewById<RecyclerView>(R.id.rvDispositivosBt)
        val fab = findViewById<View>(R.id.fabRefresh)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            header.setPadding(header.paddingLeft, systemBars.top, header.paddingRight, (12 * resources.displayMetrics.density).toInt())
            rv.setPadding(rv.paddingLeft, rv.paddingTop, rv.paddingRight, systemBars.bottom + (80 * resources.displayMetrics.density).toInt())
            val params = fab.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.bottomMargin = systemBars.bottom + (20 * resources.displayMetrics.density).toInt()
            fab.layoutParams = params
            insets
        }

        editDeviceId = intent.getIntExtra("EXTRA_DEVICE_ID", -1)

        inicializarVistas()
        configurarUI()
        configurarEventos()

        if (editDeviceId != -1) {
            cargarDatosDispositivo()
        } else {
            pedirPermisos()
        }
    }

    private fun inicializarVistas() {
        tvDispositivoGuardado = findViewById(R.id.tvDispositivoGuardado)
        tvEstadoConexion = findViewById(R.id.tvEstadoConexion)
        viewStatusDot = findViewById(R.id.viewStatusDot)
        ivIconoEstado = findViewById(R.id.ivIconoEstado)
        
        progressCargando = findViewById(R.id.progressCargando)
        rvDispositivos = findViewById(R.id.rvDispositivosBt)
    }

    private fun configurarUI() {
        listAdapter = BluetoothDeviceAdapter { resultado ->
            val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(resultado.mac)
            intentarConectarDispositivo(device)
        }
        
        rvDispositivos.layoutManager = LinearLayoutManager(this)
        rvDispositivos.adapter = listAdapter

        actualizarUIEstado()
    }

    private fun configurarEventos() {
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        findViewById<View>(R.id.fabRefresh).setOnClickListener { pedirPermisos() }
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
            Snackbars.warning(findViewById(android.R.id.content), "Activa el GPS para escanear", Toast.LENGTH_LONG).show()
            startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        } else {
            iniciarEscaneo()
        }
    }

    @SuppressLint("MissingPermission")
    private fun iniciarEscaneo() {
        listAdapter.limpiar()
        progressCargando.visibility = View.VISIBLE

        gestorBluetooth.iniciarEscaneo(
            alEncontrarDispositivo = { resultado ->
                runOnUiThread { listAdapter.agregarDispositivo(resultado) }
            },
            alFinalizarEscaneo = {
                runOnUiThread {
                    if (isDestroyed || isFinishing) return@runOnUiThread
                    progressCargando.visibility = View.INVISIBLE
                }
            }
        )
    }

    @SuppressLint("MissingPermission")
    private fun intentarConectarDispositivo(device: BluetoothDevice) {
        gestorBluetooth.detenerEscaneo()
        
        tvEstadoConexion.text = "Conectando..."
        tvEstadoConexion.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
        viewStatusDot.background = ContextCompat.getDrawable(this, R.drawable.dot_green)
        
        progressCargando.visibility = View.VISIBLE
        
        lifecycleScope.launch(Dispatchers.IO) {
            var exito = false
            repeat(2) {
                if (!exito) {
                    exito = BluetoothController.connectDevice(device)
                    if (!exito) delay(1500)
                }
            }

            withContext(Dispatchers.Main) {
                progressCargando.visibility = View.INVISIBLE
                
                if (exito) {
                    mostrarAlertaConfiguracion(device)
                    Snackbars.success(findViewById(android.R.id.content), "¡Vínculo Establecido!", Toast.LENGTH_SHORT).show()
                } else {
                    Snackbars.error(findViewById(android.R.id.content), "No se pudo conectar", Toast.LENGTH_LONG).show()
                }
                actualizarUIEstado()
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

        @SuppressLint("MissingPermission")
        val hardwareName = if (device != null) {
            device.name ?: "Hardware Encontrado"
        } else {
            editDevParam?.nombreBluetooth ?: "Dispositivo Vinculado"
        }
        tvTarget.text = hardwareName

        val tipos = listOf("Luces", "Bocinas", "Ventilador", "Televisión")
        val adapter = ArrayAdapter(themedContext, android.R.layout.simple_spinner_dropdown_item, tipos)
        etType.setAdapter(adapter)

        etType.setOnClickListener { etType.showDropDown() }

        if (editDevParam != null) {
            etName.setText(editDevParam.nombre)
            etType.setText(editDevParam.tipo, false)
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

    private fun startDeleteDevice(id: Int) {
        val sesionPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val token = sesionPref.getString("apiToken", "") ?: ""
        if (token.isEmpty()) return
        val bearer = "Bearer $token"
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.deviceService.deleteDispositivo(bearer, id)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        existingDevice?.let { db.dispositivoDao().deleteDispositivo(it) }
                        Snackbars.success(findViewById(android.R.id.content), "Dispositivo eliminado", Toast.LENGTH_SHORT).show()
                        // Aquí sí podríamos salir si eliminamos, o quedarnos. Vamos a quedarnos.
                    } else {
                        manejarErrorServidor(response.code())
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Snackbars.error(findViewById(android.R.id.content), "Error de red al eliminar", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun guardarEnServidor(dispositivo: Dispositivo) {
        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val token = sharedPref.getString("apiToken", "") ?: ""
        if (token.isEmpty()) return
        val bearer = "Bearer $token"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = if (dispositivo.id == 0) {
                    RetrofitClient.deviceService.createDispositivo(bearer, dispositivo)
                } else {
                    val updateResp = RetrofitClient.deviceService.updateDispositivo(bearer, dispositivo.id, dispositivo)
                    if (updateResp.isSuccessful) {
                        retrofit2.Response.success(com.example.android.network.ApiResponse(true, 200, dispositivo))
                    } else {
                        retrofit2.Response.error(updateResp.code(), updateResp.errorBody()!!)
                    }
                }

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val guardado = response.body()?.data
                        if (guardado != null) {
                            db.dispositivoDao().insertDispositivo(guardado)
                        }
                        Snackbars.success(findViewById(android.R.id.content), "Guardado exitoso", Toast.LENGTH_SHORT).show()
                        // Eliminamos finish() para permitir configurar otros dispositivos
                    } else {
                        manejarErrorServidor(response.code())
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Snackbars.error(findViewById(android.R.id.content), "Error de red", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun manejarErrorServidor(codigo: Int) {
        val msg = when(codigo) {
            401 -> "Sesión expirada"
            else -> "Error $codigo"
        }
        Snackbars.error(findViewById(android.R.id.content), msg, Toast.LENGTH_LONG).show()
    }

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
    }
}
