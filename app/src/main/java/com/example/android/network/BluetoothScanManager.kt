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
import android.location.LocationManager
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

    fun isGpsEnabled(): Boolean {
        val locationManager = contexto.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    fun tienePermisosNecesarios(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        android.util.Log.d("BluetoothScanManager", "ACCESS_FINE_LOCATION: $fineLocation")
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scan = ContextCompat.checkSelfPermission(contexto, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            val connect = ContextCompat.checkSelfPermission(contexto, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            android.util.Log.d("BluetoothScanManager", "BLUETOOTH_SCAN: $scan, BLUETOOTH_CONNECT: $connect")
            fineLocation && scan && connect
        } else {
            fineLocation
        }
    }

    fun permisosNecesarios(): Array<String> {
        val list = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_SCAN)
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        return list.toTypedArray()
    }

    @SuppressLint("MissingPermission")
    fun iniciarEscaneo(
        alEncontrarDispositivo: (ResultadoDispositivoBt) -> Unit,
        alFinalizarEscaneo: () -> Unit
    ) {
        // Detener escaneo previo si existe para evitar fugas de memoria y bloqueos
        detenerEscaneo()

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
                            val nombre = try { 
                                it.name ?: "Dispositivo desconocido" 
                            } catch (e: SecurityException) { 
                                android.util.Log.w("BluetoothScanManager", "No se pudo obtener el nombre del dispositivo: ${e.message}")
                                "Dispositivo Protegido" 
                            }
                            val mac    = it.address ?: return
                            this@BluetoothScanManager.alEncontrarDispositivo?.invoke(
                                ResultadoDispositivoBt(nombre, mac)
                            )
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        detenerEscaneo()
                        this@BluetoothScanManager.alFinalizarEscaneo?.invoke()
                    }
                }
            }
        }

        // Registrar con el flag RECEIVER_EXPORTED para compatibilidad con Android 14+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            contexto.registerReceiver(receptorEventos, filtro, Context.RECEIVER_EXPORTED)
        } else {
            contexto.registerReceiver(receptorEventos, filtro)
        }

        if (adaptadorBluetooth?.isDiscovering == true) {
            try {
                android.util.Log.d("BluetoothScanManager", "Cancelando descubrimiento previo...")
                adaptadorBluetooth?.cancelDiscovery()
            } catch (e: SecurityException) {
                android.util.Log.e("BluetoothScanManager", "Error de seguridad al cancelar discovery", e)
            }
        }
        
        // Iniciar el descubrimiento. startDiscovery es asíncrono y no bloquea significativamente,
        // pero lo envolvemos en un bloque seguro.
        try {
            android.util.Log.d("BluetoothScanManager", "Iniciando startDiscovery()...")
            val exito = adaptadorBluetooth?.startDiscovery() ?: false
            if (!exito) {
                android.util.Log.e("BluetoothScanManager", "Fallo al iniciar discovery (startDiscovery devolvió false)")
                this.alFinalizarEscaneo?.invoke()
            }
        } catch (e: SecurityException) {
            android.util.Log.e("BluetoothScanManager", "Error de seguridad al iniciar discovery", e)
            this.alFinalizarEscaneo?.invoke()
        } catch (e: Exception) {
            android.util.Log.e("BluetoothScanManager", "Error inesperado al iniciar discovery", e)
            this.alFinalizarEscaneo?.invoke()
        }
    }

    @SuppressLint("MissingPermission")
    fun detenerEscaneo() {
        adaptadorBluetooth?.cancelDiscovery()
        try { receptorEventos?.let { contexto.unregisterReceiver(it) } } catch (_: Exception) {}
        receptorEventos = null
    }
}
