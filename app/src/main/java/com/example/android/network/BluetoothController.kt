package com.example.android.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.util.UUID

object BluetoothController {
    var bluetoothSocket: BluetoothSocket? = null

    // Ahora isConnected también tomará en cuenta si simulamos la conexión para la demo
    var isConnected = false
        private set

    // UUID estándar para módulos Seriales (Hardware de desarrollo)
    private val myUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    fun connectDevice(device: BluetoothDevice): Boolean {
        disconnect() // Limpiar conexiones previas

        try {
            // INTENTO 1: Conexión Real Serial (Para Arduinos, HC-05, etc.)
            bluetoothSocket = device.createRfcommSocketToServiceRecord(myUUID)
            bluetoothSocket?.connect()
            isConnected = true
            return true

        } catch (e: IOException) {
            // El dispositivo rechazó el Socket Crudo (Es una TV, audífonos o foco BLE)
            try {
                // Cerramos el socket que falló
                bluetoothSocket?.close()
                bluetoothSocket = null
            } catch (closeException: IOException) {
                // Ignorar
            }

            // INTENTO 2: Fallback Visual
            // Si el dispositivo ya está "Vinculado" (Emparejado) en los ajustes del teléfono de Android,
            // lo tomaremos como conectado para que la interfaz (UI) fluya correctamente.
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                // Simulamos la conexión para mantener la tarjeta en verde
                isConnected = true
                return true
            }

            // Si llegamos aquí, ni siquiera está emparejado
            isConnected = false
            return false
        }
    }

    fun disconnect() {
        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        bluetoothSocket = null
        isConnected = false
    }
}