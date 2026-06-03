package com.example.android.network

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
import androidx.core.content.ContextCompat

data class ResultadoDispositivoBt(
    val nombre: String,
    val mac: String
)

class BluetoothScanManager(private val contexto: Context) {

    private val adaptadorBluetooth: BluetoothAdapter? by lazy {
        val gestorBt = contexto.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        gestorBt?.adapter
    }

    private var alEncontrarDispositivo: ((ResultadoDispositivoBt) -> Unit)? = null
    private var alFinalizarEscaneo: (() -> Unit)? = null
    private var receptorEventos: BroadcastReceiver? = null

    val bluetoothDisponible: Boolean get() = adaptadorBluetooth != null
    val bluetoothActivado: Boolean get() = adaptadorBluetooth?.isEnabled == true

    fun tienePermisosNecesarios(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun permisosNecesarios(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    fun iniciarEscaneo(
        alEncontrarDispositivo: (ResultadoDispositivoBt) -> Unit,
        alFinalizarEscaneo: () -> Unit
    ) {
        this.alEncontrarDispositivo = alEncontrarDispositivo
        this.alFinalizarEscaneo    = alFinalizarEscaneo

        val filtro = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }

        receptorEventos = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val dispositivo: BluetoothDevice? =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            else
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

                        dispositivo?.let {
                            val nombre = it.name ?: "Dispositivo desconocido"
                            val mac    = it.address ?: return
                            this@BluetoothScanManager.alEncontrarDispositivo?.invoke(
                                ResultadoDispositivoBt(nombre, mac)
                            )
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        this@BluetoothScanManager.alFinalizarEscaneo?.invoke()
                    }
                }
            }
        }

        contexto.registerReceiver(receptorEventos, filtro)

        if (adaptadorBluetooth?.isDiscovering == true) {
            adaptadorBluetooth?.cancelDiscovery()
        }
        adaptadorBluetooth?.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun detenerEscaneo() {
        adaptadorBluetooth?.cancelDiscovery()
        try { receptorEventos?.let { contexto.unregisterReceiver(it) } } catch (_: Exception) {}
        receptorEventos = null
    }
}
