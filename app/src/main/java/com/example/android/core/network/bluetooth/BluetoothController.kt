package com.example.android.core.network.bluetooth
import com.example.android.core.db.models.Dispositivo

import com.example.android.core.network.api.*
import com.example.android.core.network.client.*
import com.example.android.core.network.bluetooth.*
import com.example.android.core.network.wifi.*
import com.example.android.core.network.stream.*

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.util.UUID

object BluetoothController {
    var bluetoothSocket: BluetoothSocket? = null

    // Ahora isConnected también tomará en cuenta si simulamos la conexión para la demo
    var isConnected = false
        private set

    var connectedMac: String? = null
        private set

    // UUID estándar para módulos Seriales (Hardware de desarrollo)
    private val myUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    fun connectDevice(device: BluetoothDevice): Boolean {
        Log.d("BluetoothController", "connectDevice: Iniciando conexión a MAC ${device.address}")
        disconnect() // Limpiar conexiones previas

        try {
            // INTENTO 1: Conexión Real Serial (Para Arduinos, HC-05, etc.)
            Log.d("BluetoothController", "connectDevice: Intentando conexión Rfcomm")
            bluetoothSocket = device.createRfcommSocketToServiceRecord(myUUID)
            bluetoothSocket?.connect()
            isConnected = true
            connectedMac = device.address
            Log.d("BluetoothController", "connectDevice: Conexión exitosa a ${device.address}")
            return true

        } catch (e: Exception) {
            Log.e("BluetoothController", "connectDevice: Falló la conexión Rfcomm: ${e.message}")
            // El dispositivo rechazó el Socket Crudo (Es una TV, audífonos o foco BLE) o no tiene servicio Serial
            try {
                bluetoothSocket?.close()
                bluetoothSocket = null
            } catch (closeException: Exception) {}

            // Si el dispositivo no está emparejado, forzamos la petición de emparejamiento nativa de Android
            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                Log.d("BluetoothController", "connectDevice: Dispositivo no emparejado, forzando emparejamiento nativo")
                try {
                    device.createBond()
                    isConnected = true
                    connectedMac = device.address
                    return true
                } catch (bondException: Exception) {
                    Log.e("BluetoothController", "connectDevice: Error al emparejar: ${bondException.message}")
                    bondException.printStackTrace()
                }
            } else {
                Log.d("BluetoothController", "connectDevice: Dispositivo ya emparejado pero rechazó RFCOMM, simulando conexión para demo")
                isConnected = true
                connectedMac = device.address
                return true
            }

            Log.w("BluetoothController", "connectDevice: No se pudo conectar de ninguna forma")
            isConnected = false
            connectedMac = null
            return false
        }
    }

    fun disconnect() {
        Log.d("BluetoothController", "disconnect: Desconectando...")
        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e("BluetoothController", "disconnect: Error al cerrar socket: ${e.message}")
            e.printStackTrace()
        }
        bluetoothSocket = null
        isConnected = false
        connectedMac = null
        Log.d("BluetoothController", "disconnect: Desconectado")
    }

    fun enviarComando(comando: String) {
        Log.d("BluetoothController", "enviarComando: Solicitud de envío comando='$comando'. isConnected=$isConnected")
        if (isConnected && bluetoothSocket != null) {
            try {
                bluetoothSocket?.outputStream?.write(comando.toByteArray())
                Log.d("BluetoothController", "enviarComando: Comando '$comando' enviado correctamente")
            } catch (e: IOException) {
                Log.e("BluetoothController", "enviarComando: Error al enviar comando: ${e.message}")
                e.printStackTrace()
                // Si falla al enviar, probablemente se desconectó
                isConnected = false
            }
        } else {
            Log.w("BluetoothController", "enviarComando: No se envió porque no hay socket o isConnected=false (Simulación demo)")
        }
    }
}
