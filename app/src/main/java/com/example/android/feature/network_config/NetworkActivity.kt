package com.example.android.feature.network_config
import com.example.android.core.db.Dispositivo

import com.example.android.R

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
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
import com.example.android.core.network.BluetoothController
import com.example.android.core.network.BluetoothScanManager
import com.example.android.core.view.Snackbars
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NetworkActivity : AppCompatActivity() {

    private lateinit var tvDispositivoGuardado: TextView
    private lateinit var tvEstadoConexion: TextView
    private lateinit var ivIconoEstado: ImageView
    private lateinit var btnEscanear: Button
    private lateinit var progressCargando: LinearProgressIndicator
    private lateinit var lvDispositivos: ListView

    private lateinit var gestorBluetooth: BluetoothScanManager
    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private val deviceNames = mutableListOf<String>()
    private lateinit var listAdapter: ArrayAdapter<String>

    private val PREF_NAME = "BluetoothPrefs"
    private val KEY_MAC = "saved_mac_address"
    private val KEY_NAME = "saved_device_name"

    private lateinit var vistaRaiz: View

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.entries.all { it.value }) {
            verificarYEncenderBluetooth()
        } else {
            Snackbars.error(vistaRaiz, "Permisos necesarios denegados", Toast.LENGTH_LONG).show()
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) verificarGPSYScan()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = true

        setContentView(R.layout.activity_network)
        vistaRaiz = findViewById(android.R.id.content)

        tvDispositivoGuardado = findViewById(R.id.tvDispositivoGuardado)
        tvEstadoConexion = findViewById(R.id.tvEstadoConexion)
        ivIconoEstado = findViewById(R.id.ivIconoEstado)
        btnEscanear = findViewById(R.id.btnEscanear)
        progressCargando = findViewById(R.id.progressCargando)
        lvDispositivos = findViewById(R.id.lvDispositivos)

        gestorBluetooth = BluetoothScanManager(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainNetwork)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, 0)

            lvDispositivos.setPadding(lvDispositivos.paddingLeft, lvDispositivos.paddingTop, lvDispositivos.paddingRight, bars.bottom)
            lvDispositivos.clipToPadding = false

            insets
        }

        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceNames)
        lvDispositivos.adapter = listAdapter

        actualizarUIConexionLocal()

        btnEscanear.setOnClickListener { pedirPermisos() }

        lvDispositivos.setOnItemClickListener { _, _, position, _ ->
            val device = discoveredDevices[position]
            conectarYGuardarDispositivo(device)
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
            } catch (e: SecurityException) {
                android.util.Log.e("NetworkActivity", "Permiso denegado al activar Bluetooth", e)
                Snackbars.error(vistaRaiz, "Permisos insuficientes para activar Bluetooth", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.util.Log.e("NetworkActivity", "Error al activar Bluetooth", e)
                Snackbars.error(vistaRaiz, "Error al activar Bluetooth", Toast.LENGTH_LONG).show()
            }
        } else {
            verificarGPSYScan()
        }
    }

    private fun verificarGPSYScan() {
        if (!gestorBluetooth.isGpsEnabled()) {
            Snackbars.warning(vistaRaiz, "Activa el GPS para escanear dispositivos", Toast.LENGTH_LONG).show()
            startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        } else {
            iniciarEscaneo()
        }
    }

    @SuppressLint("MissingPermission")
    private fun iniciarEscaneo() {
        if (!gestorBluetooth.tienePermisosNecesarios()) {
            pedirPermisos()
            return
        }

        discoveredDevices.clear()
        deviceNames.clear()
        listAdapter.notifyDataSetChanged()

        progressCargando.visibility = View.VISIBLE
        btnEscanear.isEnabled = false
        btnEscanear.text = "Escaneando..."

        gestorBluetooth.iniciarEscaneo(
            alEncontrarDispositivo = { resultado ->
                runOnUiThread {
                    if (!discoveredDevices.any { it.address == resultado.mac }) {
                        val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(resultado.mac)
                        discoveredDevices.add(device)
                        deviceNames.add("${resultado.nombre}\n${resultado.mac}")
                        listAdapter.notifyDataSetChanged()
                    }
                }
            },
            alFinalizarEscaneo = {
                runOnUiThread {
                    if (this.isDestroyed || this.isFinishing) return@runOnUiThread
                    progressCargando.visibility = View.INVISIBLE
                    btnEscanear.isEnabled = true
                    btnEscanear.text = "Escanear Dispositivos"
                    if (discoveredDevices.isEmpty()) {
                        Snackbars.info(vistaRaiz, "No se encontraron dispositivos", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    @SuppressLint("MissingPermission")
    private fun conectarYGuardarDispositivo(device: BluetoothDevice) {
        gestorBluetooth.detenerEscaneo()
        progressCargando.visibility = View.VISIBLE
        btnEscanear.isEnabled = false
        Snackbars.info(vistaRaiz, "Conectando al Hardware...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            val exito = BluetoothController.connectDevice(device)

            withContext(Dispatchers.Main) {
                progressCargando.visibility = View.INVISIBLE
                btnEscanear.isEnabled = true
                btnEscanear.text = "Escanear Dispositivos"

                if (exito) {
                    val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    with(prefs.edit()) {
                        putString(KEY_NAME, device.name ?: "Dispositivo")
                        putString(KEY_MAC, device.address)
                        apply()
                    }
                    Snackbars.success(vistaRaiz, "¡Conexión Establecida!", Toast.LENGTH_SHORT).show()
                } else {
                    Snackbars.error(vistaRaiz, "Error de conexión con el hardware", Toast.LENGTH_LONG).show()
                }
                actualizarUIConexionLocal()
            }
        }
    }

    private fun actualizarUIConexionLocal() {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        tvDispositivoGuardado.text = prefs.getString(KEY_NAME, "Ninguno")

        if (BluetoothController.isConnected) {
            tvEstadoConexion.text = "Conectado"
            tvEstadoConexion.setTextColor(ContextCompat.getColor(this, R.color.teal_primary))
            ivIconoEstado.setColorFilter(ContextCompat.getColor(this, R.color.teal_primary))
        } else {
            tvEstadoConexion.text = "Desconectado"
            tvEstadoConexion.setTextColor(android.graphics.Color.parseColor("#F44336"))
            ivIconoEstado.setColorFilter(android.graphics.Color.parseColor("#F44336"))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gestorBluetooth.detenerEscaneo()
    }
}
