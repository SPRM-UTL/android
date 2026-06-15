package com.example.android

import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.android.db.AppDatabase
import com.example.android.db.Dispositivo
import com.example.android.network.ApiHandler
import com.example.android.network.BluetoothScanManager
import com.example.android.network.RetrofitClient
import com.example.android.ui.BluetoothDeviceAdapter
import com.example.android.ui.DeviceAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeviceActivity : AppCompatActivity() {

    private lateinit var adaptadorDispositivos: DeviceAdapter
    private lateinit var baseDatos: AppDatabase
    private lateinit var gestorBluetooth: BluetoothScanManager

    private var accionPendienteBt: (() -> Unit)? = null

    private val lanzadorActivarBt = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* El dialogo manejara el estado según si el usuario activó o no el bluetooth */ }

    private val lanzadorPermisos = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permisos ->
        if (permisos.values.all { it }) {
            accionPendienteBt?.invoke()
        } else {
            Toast.makeText(this, "Se necesitan permisos de Bluetooth para escanear", Toast.LENGTH_LONG).show()
        }
        accionPendienteBt = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = true

        setContentView(R.layout.activity_device)

        val vistaRaiz = findViewById<View>(R.id.mainDevice)
        ViewCompat.setOnApplyWindowInsetsListener(vistaRaiz) { v, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(barras.left, barras.top, barras.right, 0)
            
            // Ajustar el margen del FAB para que no lo tape la barra de navegación
            val fab = findViewById<View>(R.id.fabAddDevice)
            val params = fab.layoutParams as android.view.ViewGroup.MarginLayoutParams
            params.bottomMargin = barras.bottom + (16 * resources.displayMetrics.density).toInt()
            fab.layoutParams = params

            // Añadir padding al recycler para que el último item se vea bien
            val rv = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvDevices)
            rv.setPadding(rv.paddingLeft, rv.paddingTop, rv.paddingRight, barras.bottom)

            insets
        }

        baseDatos     = AppDatabase.getDatabase(this)
        gestorBluetooth = BluetoothScanManager(this)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        configurarListaDispositivos()

        lifecycleScope.launch {
            baseDatos.dispositivoDao().getAllDispositivos().collectLatest { lista ->
                adaptadorDispositivos.submitList(lista)
            }
        }

        sincronizarDesdeServidor()

        val dispositivoId = intent.getIntExtra("DISPOSITIVO_ID", -1)
        val abrirAgregar  = intent.getBooleanExtra("ABRIR_FORMULARIO_AGREGAR", false)

        val fab = findViewById<FloatingActionButton>(R.id.fabAddDevice)
        fab.setOnClickListener { mostrarDialogoDispositivo(null) }

        when {
            dispositivoId != -1 -> {
                fab.visibility = View.GONE
                lifecycleScope.launch {
                    val dispositivo = baseDatos.dispositivoDao().getDispositivoById(dispositivoId)
                    mostrarDialogoDispositivo(dispositivo)
                }
            }
            abrirAgregar -> mostrarDialogoDispositivo(null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gestorBluetooth.detenerEscaneo()
    }

    private fun configurarListaDispositivos() {
        val rvDispositivos = findViewById<RecyclerView>(R.id.rvDevices)
        rvDispositivos.layoutManager = GridLayoutManager(this, 2)
        adaptadorDispositivos = DeviceAdapter(
            onEditClick   = { mostrarDialogoDispositivo(it) },
            onDeleteClick = { eliminarDispositivo(it) },
            onToggleClick = { dispositivo, encendido ->
                Toast.makeText(this, "${dispositivo.nombre}: ${if (encendido) "Encendido" else "Apagado"}", Toast.LENGTH_SHORT).show()
            }
        )
        rvDispositivos.adapter = adaptadorDispositivos
    }

    private fun sincronizarDesdeServidor() {
        lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = this@DeviceActivity,
                showLoading = false,
                apiCall = {
                    val preferencias = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
                    val token = preferencias.getString("apiToken", "") ?: ""
                    RetrofitClient.deviceService.getDispositivos("Bearer $token")
                },
                onSuccess = { respuesta ->
                    val dispositivosApi = respuesta.data
                    withContext(Dispatchers.IO) {
                        baseDatos.dispositivoDao().deleteAllDispositivos()
                        baseDatos.dispositivoDao().insertAll(dispositivosApi)
                    }
                },
                onError = { errorMsg ->
                    Log.e("DeviceActivity", "Error al sincronizar dispositivos: $errorMsg")
                }
            )
        }
    }

    private fun mostrarDialogoDispositivo(dispositivoExistente: Dispositivo?) {
        val dialogo = Dialog(this)
        dialogo.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialogo.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogo.setCanceledOnTouchOutside(false)
        dialogo.setCancelable(false)
        dialogo.setContentView(R.layout.dialog_device_form)
        dialogo.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val tvTitulo        = dialogo.findViewById<android.widget.TextView>(R.id.tvDialogTitle)
        val etNombre        = dialogo.findViewById<TextInputEditText>(R.id.etDeviceName)
        val etTipo          = dialogo.findViewById<MaterialAutoCompleteTextView>(R.id.etDeviceType)
        val btnEscanear     = dialogo.findViewById<MaterialButton>(R.id.btnScanBluetooth)
        val layoutCargando  = dialogo.findViewById<LinearLayout>(R.id.layoutScanProgress)
        val rvBt            = dialogo.findViewById<RecyclerView>(R.id.rvBluetoothDevices)
        val layoutNombreBt  = dialogo.findViewById<TextInputLayout>(R.id.layoutBtName)
        val layoutMacBt     = dialogo.findViewById<TextInputLayout>(R.id.layoutBtMac)
        val etNombreBt      = dialogo.findViewById<TextInputEditText>(R.id.etBtName)
        val etMacBt         = dialogo.findViewById<TextInputEditText>(R.id.etBtMac)

        tvTitulo.text = if (dispositivoExistente != null) "Editar Dispositivo" else "Añadir Dispositivo"

        val tiposDisponibles = listOf("Luces", "Bocinas", "Ventilador", "Televisión")
        etTipo.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, tiposDisponibles))

        if (dispositivoExistente != null) {
            etNombre.setText(dispositivoExistente.nombre)
            etTipo.setText(dispositivoExistente.tipo, false)
            if (!dispositivoExistente.macBluetooth.isNullOrBlank()) {
                etNombreBt.setText(dispositivoExistente.nombreBluetooth ?: dispositivoExistente.nombre)
                etMacBt.setText(dispositivoExistente.macBluetooth)
                layoutNombreBt.visibility = View.VISIBLE
                layoutMacBt.visibility    = View.VISIBLE
            }
        }

        val adaptadorBt = BluetoothDeviceAdapter { dispositivoBt ->
            if (etNombre.text.isNullOrBlank()) etNombre.setText(dispositivoBt.nombre)
            etNombreBt.setText(dispositivoBt.nombre)
            etMacBt.setText(dispositivoBt.mac)
            layoutNombreBt.visibility = View.VISIBLE
            layoutMacBt.visibility    = View.VISIBLE
            rvBt.visibility           = View.GONE
            layoutCargando.visibility = View.GONE
            gestorBluetooth.detenerEscaneo()
        }
        rvBt.layoutManager = LinearLayoutManager(this)
        rvBt.adapter       = adaptadorBt

        btnEscanear.setOnClickListener {
            ejecutarEscaneo(adaptadorBt, layoutCargando, rvBt, btnEscanear)
        }

        dialogo.findViewById<Button>(R.id.btnSave).setOnClickListener {
            val nombre   = etNombre.text.toString().trim()
            val tipo     = etTipo.text.toString().trim()
            val mac      = etMacBt.text.toString().trim()
            val nombreBt = etNombreBt.text.toString().trim()

            var hayError = false

            if (nombre.isBlank()) {
                etNombre.error = "Campo requerido"
                hayError = true
            } else {
                etNombre.error = null
            }

            val tiposValidos = listOf("Luces", "Bocinas", "Ventilador", "Televisión")
            if (tipo.isBlank() || tipo !in tiposValidos) {
                etTipo.error = "Selecciona un tipo de dispositivo"
                hayError = true
            } else {
                etTipo.error = null
            }

            val formatoMacValido = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
            if (mac.isNotBlank() && !mac.matches(formatoMacValido)) {
                etMacBt.error = "Formato inválido (ej: AA:BB:CC:DD:EE:FF)"
                hayError = true
            } else {
                etMacBt.error = null
            }

            if (hayError) return@setOnClickListener

            val fechaSincronizacion = if (mac.isNotBlank()) {
                java.time.LocalDateTime.now().toString()
            } else {
                dispositivoExistente?.fechaSincronizacion
            }

            val nuevoDispositivo = Dispositivo(
                id                  = dispositivoExistente?.id ?: 0,
                nombre              = nombre,
                tipo                = tipo,
                accion              = "Encendido",
                comandoBluetooth    = "BT_ON",
                icono               = "ic_default",
                macBluetooth        = mac.ifBlank { null },
                nombreBluetooth     = nombreBt.ifBlank { null },
                fechaSincronizacion = fechaSincronizacion
            )
            guardarDispositivo(nuevoDispositivo, esActualizacion = dispositivoExistente != null)
            dialogo.dismiss()
        }

        dialogo.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            gestorBluetooth.detenerEscaneo()
            dialogo.dismiss()
        }

        dialogo.setOnDismissListener { gestorBluetooth.detenerEscaneo() }
        dialogo.show()
    }

    private fun ejecutarEscaneo(
        adaptadorBt: BluetoothDeviceAdapter,
        layoutCargando: LinearLayout,
        rvBt: RecyclerView,
        btnEscanear: MaterialButton
    ) {
        if (!gestorBluetooth.bluetoothDisponible) {
            Toast.makeText(this, "Este dispositivo no soporta Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        if (!gestorBluetooth.tienePermisosNecesarios()) {
            accionPendienteBt = { ejecutarEscaneo(adaptadorBt, layoutCargando, rvBt, btnEscanear) }
            lanzadorPermisos.launch(gestorBluetooth.permisosNecesarios())
            return
        }

        if (!gestorBluetooth.isGpsEnabled()) {
            Toast.makeText(this, "Por favor, activa la ubicación (GPS) para escanear dispositivos", Toast.LENGTH_LONG).show()
            startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }

        if (!gestorBluetooth.bluetoothActivado) {
            try {
                lanzadorActivarBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } catch (e: SecurityException) {
                Log.e("DeviceActivity", "Permiso denegado al intentar activar Bluetooth", e)
                Toast.makeText(this, "Error: No se tienen los permisos para activar Bluetooth", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e("DeviceActivity", "Error inesperado al solicitar activación de Bluetooth", e)
                Toast.makeText(this, "No se pudo solicitar la activación de Bluetooth", Toast.LENGTH_LONG).show()
            }
            return
        }

        adaptadorBt.limpiar()
        rvBt.visibility           = View.VISIBLE
        layoutCargando.visibility = View.VISIBLE
        btnEscanear.text          = "Escaneando..."
        btnEscanear.isEnabled     = false

        gestorBluetooth.iniciarEscaneo(
            alEncontrarDispositivo = { dispositivoBt ->
                runOnUiThread { adaptadorBt.agregarDispositivo(dispositivoBt) }
            },
            alFinalizarEscaneo = {
                runOnUiThread {
                    layoutCargando.visibility = View.GONE
                    btnEscanear.text          = "Escanear de nuevo"
                    btnEscanear.isEnabled     = true
                    if (adaptadorBt.itemCount == 0) {
                        Toast.makeText(this, "No se encontraron dispositivos cercanos", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    private fun guardarDispositivo(dispositivo: Dispositivo, esActualizacion: Boolean) {
        lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = this@DeviceActivity,
                showLoading = true,
                loadingTitle = "Guardando",
                loadingMessage = "Sincronizando dispositivo...",
                apiCall = {
                    val preferencias = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
                    val encabezado = "Bearer ${preferencias.getString("apiToken", "") ?: ""}"
                    
                    if (esActualizacion) {
                        RetrofitClient.deviceService.updateDispositivo(encabezado, dispositivo.id, dispositivo)
                        retrofit2.Response.success(com.example.android.network.ApiResponse(true, 200, dispositivo))
                    } else {
                        RetrofitClient.deviceService.createDispositivo(encabezado, dispositivo)
                    }
                },
                onSuccess = { respuesta ->
                    val guardado = respuesta.data
                    if (guardado != null) {
                        withContext(Dispatchers.IO) {
                            baseDatos.dispositivoDao().insertDispositivo(guardado)
                        }
                        Toast.makeText(this@DeviceActivity, "Guardado exitoso", Toast.LENGTH_SHORT).show()
                    }
                },
                onError = { errorMsg ->
                    if (errorMsg != "Sesión expirada") {
                        Toast.makeText(this@DeviceActivity, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    private fun eliminarDispositivo(dispositivo: Dispositivo) {
        lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = this@DeviceActivity,
                showLoading = true,
                loadingTitle = "Eliminando",
                loadingMessage = "Eliminando dispositivo...",
                apiCall = {
                    val preferencias = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
                    val encabezado = "Bearer ${preferencias.getString("apiToken", "") ?: ""}"
                    RetrofitClient.deviceService.deleteDispositivo(encabezado, dispositivo.id)
                },
                onSuccess = {
                    withContext(Dispatchers.IO) {
                        baseDatos.dispositivoDao().deleteDispositivo(dispositivo)
                    }
                    Toast.makeText(this@DeviceActivity, "Dispositivo eliminado", Toast.LENGTH_SHORT).show()
                },
                onError = { errorMsg ->
                    if (errorMsg != "Sesión expirada") {
                        Toast.makeText(this@DeviceActivity, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}