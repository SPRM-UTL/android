package com.example.android

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.android.network.BluetoothController
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

    private var bluetoothAdapter: BluetoothAdapter? = null
    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private val deviceNames = mutableListOf<String>()
    private lateinit var listAdapter: ArrayAdapter<String>

    private val PREF_NAME = "BluetoothPrefs"
    private val KEY_MAC = "saved_mac_address"
    private val KEY_NAME = "saved_device_name"

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.entries.all { it.value }) verificarYEncenderBluetooth()
        else Toast.makeText(this, "Permisos denegados", Toast.LENGTH_SHORT).show()
    }

    private val enableBluetoothLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) iniciarEscaneo()
    }

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothDevice.ACTION_FOUND == intent.action) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                device?.let {
                    if (!discoveredDevices.contains(it) && it.name != null) {
                        discoveredDevices.add(it)
                        deviceNames.add("${it.name}\n${it.address}")
                        listAdapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_network)

        tvDispositivoGuardado = findViewById(R.id.tvDispositivoGuardado)
        tvEstadoConexion = findViewById(R.id.tvEstadoConexion)
        ivIconoEstado = findViewById(R.id.ivIconoEstado)
        btnEscanear = findViewById(R.id.btnEscanear)
        progressCargando = findViewById(R.id.progressCargando)
        lvDispositivos = findViewById(R.id.lvDispositivos)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

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
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) verificarYEncenderBluetooth() else requestPermissionLauncher.launch(missing.toTypedArray())
    }

    @SuppressLint("MissingPermission")
    private fun verificarYEncenderBluetooth() {
        if (bluetoothAdapter?.isEnabled == false) {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else {
            iniciarEscaneo()
        }
    }

    @SuppressLint("MissingPermission")
    private fun iniciarEscaneo() {
        discoveredDevices.clear()
        deviceNames.clear()
        listAdapter.notifyDataSetChanged()
        progressCargando.visibility = View.VISIBLE
        registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        bluetoothAdapter?.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    private fun conectarYGuardarDispositivo(device: BluetoothDevice) {
        bluetoothAdapter?.cancelDiscovery()
        progressCargando.visibility = View.VISIBLE
        btnEscanear.isEnabled = false
        Toast.makeText(this, "Conectando al Socket Hardware...", Toast.LENGTH_SHORT).show()

        // Hacemos la conexión real en un hilo secundario
        lifecycleScope.launch(Dispatchers.IO) {
            val exito = BluetoothController.connectDevice(device)

            withContext(Dispatchers.Main) {
                progressCargando.visibility = View.INVISIBLE
                btnEscanear.isEnabled = true

                if (exito) {
                    val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    with(prefs.edit()) {
                        putString(KEY_NAME, device.name ?: "Dispositivo")
                        putString(KEY_MAC, device.address)
                        apply()
                    }
                    Toast.makeText(this@NetworkActivity, "¡Conexión Física Establecida!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@NetworkActivity, "Error: El hardware rechazó la conexión Socket", Toast.LENGTH_LONG).show()
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
        try { unregisterReceiver(receiver) } catch (e: Exception) {}
    }
}