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
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.android.db.AppDatabase
import com.example.android.db.Dispositivo
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

    // Acción pendiente que se ejecuta después de que el usuario concede permisos BT
    private var accionPendienteBt: (() -> Unit)? = null

    // Solicita al usuario activar el Bluetooth si está apagado
    private val lanzadorActivarBt = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* El diálogo manejará el estado según si el usuario activó o no el BT */ }

    // Pide permisos de Bluetooth en tiempo de ejecución (requerido en Android 12+)
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
        setContentView(R.layout.activity_device)

        val vistaRaiz = findViewById<View>(R.id.mainDevice)
        ViewCompat.setOnApplyWindowInsetsListener(vistaRaiz) { v, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        baseDatos     = AppDatabase.getDatabase(this)
        gestorBluetooth = BluetoothScanManager(this)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        configurarListaDispositivos()

        findViewById<FloatingActionButton>(R.id.fabAddDevice).setOnClickListener {
            mostrarDialogoDispositivo(null)
        }

        // Observar la base de datos local y actualizar la lista en pantalla
        lifecycleScope.launch {
            baseDatos.dispositivoDao().getAllDispositivos().collectLatest { lista ->
                adaptadorDispositivos.submitList(lista)
            }
        }

        sincronizarDesdeServidor()
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

    /** Descarga los dispositivos del servidor y actualiza la base de datos local */
    private fun sincronizarDesdeServidor() {
        val preferencias = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val token = preferencias.getString("apiToken", "") ?: ""
        val encabezado = "Bearer $token"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val respuesta = RetrofitClient.deviceService.getDispositivos(encabezado)
                if (respuesta.isSuccessful) {
                    val dispositivosApi = respuesta.body()?.data ?: emptyList()
                    baseDatos.dispositivoDao().deleteAllDispositivos()
                    baseDatos.dispositivoDao().insertAll(dispositivosApi)
                }
            } catch (e: Exception) {
                Log.e("DeviceActivity", "Error al sincronizar dispositivos", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DeviceActivity, "Error de red al sincronizar", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ─────────────────────── DIÁLOGO AGREGAR / EDITAR ───────────────────────

    private fun mostrarDialogoDispositivo(dispositivoExistente: Dispositivo?) {
        val dialogo = Dialog(this)
        dialogo.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialogo.window?.setBackgroundDrawableResource(android.R.color.transparent)
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

        // Si es edición, rellenar los campos con los datos actuales
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
            // Si el nombre personalizado está vacío, se sugiere el nombre del BT encontrado
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

            if (nombre.isBlank()) {
                etNombre.error = "Campo requerido"
                return@setOnClickListener
            }

            // La fecha de sincronización se actualiza solo cuando se vincula un dispositivo BT
            val fechaSincronizacion = if (mac.isNotBlank()) {
                java.time.LocalDateTime.now().toString()
            } else {
                dispositivoExistente?.fechaSincronizacion
            }

            val nuevoDispositivo = Dispositivo(
                id                  = dispositivoExistente?.id ?: 0,
                nombre              = nombre,
                tipo                = tipo.ifBlank { null },
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

    // ─────────────────────── ESCANEO BLUETOOTH ───────────────────────

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

        if (!gestorBluetooth.bluetoothActivado) {
            lanzadorActivarBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        if (!gestorBluetooth.tienePermisosNecesarios()) {
            accionPendienteBt = { ejecutarEscaneo(adaptadorBt, layoutCargando, rvBt, btnEscanear) }
            lanzadorPermisos.launch(gestorBluetooth.permisosNecesarios())
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

    // ─────────────────────── OPERACIONES EN SERVIDOR ───────────────────────

    private fun guardarDispositivo(dispositivo: Dispositivo, esActualizacion: Boolean) {
        val preferencias = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val encabezado   = "Bearer ${preferencias.getString("apiToken", "") ?: ""}"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val respuesta = if (esActualizacion) {
                    RetrofitClient.deviceService.updateDispositivo(encabezado, dispositivo.id, dispositivo)
                    retrofit2.Response.success(com.example.android.network.ApiResponse(true, 200, dispositivo))
                } else {
                    RetrofitClient.deviceService.createDispositivo(encabezado, dispositivo)
                }

                if (respuesta.isSuccessful) {
                    val guardado = respuesta.body()?.data
                    if (guardado != null) {
                        baseDatos.dispositivoDao().insertDispositivo(guardado)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@DeviceActivity, "Guardado exitoso", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@DeviceActivity, "Error al guardar en el servidor", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("DeviceActivity", "Error al guardar dispositivo", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DeviceActivity, "Error de red", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun eliminarDispositivo(dispositivo: Dispositivo) {
        val preferencias = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val encabezado   = "Bearer ${preferencias.getString("apiToken", "") ?: ""}"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val respuesta = RetrofitClient.deviceService.deleteDispositivo(encabezado, dispositivo.id)
                if (respuesta.isSuccessful) {
                    baseDatos.dispositivoDao().deleteDispositivo(dispositivo)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@DeviceActivity, "Dispositivo eliminado", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("DeviceActivity", "Error al eliminar dispositivo", e)
            }
        }
    }
}