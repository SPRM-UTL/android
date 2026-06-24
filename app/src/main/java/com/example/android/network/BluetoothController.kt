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

        } catch (e: Exception) {
            // El dispositivo rechazó el Socket Crudo (Es una TV, audífonos o foco BLE) o no tiene servicio Serial
            try {
                bluetoothSocket?.close()
                bluetoothSocket = null
            } catch (closeException: Exception) {}

            // Si el dispositivo no está emparejado, forzamos la petición de emparejamiento nativa de Android
            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                try {
                    device.createBond()
                    isConnected = true
                    return true
                } catch (bondException: Exception) {
                    bondException.printStackTrace()
                }
            } else {
                isConnected = true
                return true
            }

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

    fun enviarComando(comando: String) {
        if (isConnected && bluetoothSocket != null) {
            try {
                bluetoothSocket?.outputStream?.write(comando.toByteArray())
            } catch (e: IOException) {
                e.printStackTrace()
                // Si falla al enviar, probablemente se desconectó
                isConnected = false
            }
        }
    }
}