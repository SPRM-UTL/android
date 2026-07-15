package com.example.android.core.network
import com.example.android.core.db.Dispositivo

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
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

    companion object {
        private var lastBleScanTime = 0L
    }

    private val adaptadorBluetooth: BluetoothAdapter? by lazy {
        val gestorBt = contexto.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        gestorBt?.adapter
    }

    private var alEncontrarDispositivo: ((ResultadoDispositivoBt) -> Unit)? = null
    private var alFinalizarEscaneo: (() -> Unit)? = null
    private var leScanner: BluetoothLeScanner? = null
    private var leScanCallback: ScanCallback? = null
    
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

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

        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = Runnable {
            detenerEscaneo()
            this.alFinalizarEscaneo?.invoke()
        }
        handler.postDelayed(timeoutRunnable!!, 15000) // 15 seconds timeout

        if (adaptadorBluetooth?.isDiscovering == true) {
            try {
                android.util.Log.d("BluetoothScanManager", "Cancelando descubrimiento previo...")
                adaptadorBluetooth?.cancelDiscovery()
            } catch (e: SecurityException) {
                android.util.Log.e("BluetoothScanManager", "Error de seguridad al cancelar discovery", e)
            }
        }

        // Iniciar también escaneo BLE
        try {
            leScanner = adaptadorBluetooth?.bluetoothLeScanner
            if (leScanner != null) {
                leScanCallback = object : ScanCallback() {
                    @SuppressLint("MissingPermission")
                    override fun onScanResult(callbackType: Int, result: ScanResult?) {
                        val dispositivo = result?.device
                        dispositivo?.let {
                            if (it.bondState == BluetoothDevice.BOND_BONDED) {
                                android.util.Log.d("BluetoothScanManager", "Ignorando dispositivo ya vinculado: ${it.address}")
                                return@let
                            }
                            val scanName = result?.scanRecord?.deviceName
                            val nombre = try {
                                scanName ?: it.name ?: "Dispositivo Desconocido"
                            } catch (e: SecurityException) {
                                scanName ?: "Dispositivo Protegido"
                            }
                            val mac = it.address ?: return
                            android.util.Log.d("BluetoothScanManager", "BLE FOUND: $nombre - $mac")
                            this@BluetoothScanManager.alEncontrarDispositivo?.invoke(
                                ResultadoDispositivoBt(nombre, mac)
                            )
                        }
                    }
                }
                
                val now = System.currentTimeMillis()
                if (now - lastBleScanTime > 6000) {
                    lastBleScanTime = now
                    leScanner?.startScan(leScanCallback)
                    android.util.Log.d("BluetoothScanManager", "Iniciado BLE Scan")
                } else {
                    android.util.Log.w("BluetoothScanManager", "Escaneo BLE omitido por límite de frecuencia de Android (6s)")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BluetoothScanManager", "Error al iniciar BLE scan", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun detenerEscaneo() {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        adaptadorBluetooth?.cancelDiscovery()
        
        try {
            leScanCallback?.let { leScanner?.stopScan(it) }
        } catch (e: Exception) {}
        leScanCallback = null
    }
}
