package com.example.android.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.net.SocketTimeoutException

class SocketClient(private val onLog: (String) -> Unit) {

    suspend fun sendUdpBroadcast(port: Int, message: String) {
        withContext(Dispatchers.IO) {
            try {
                val socket = DatagramSocket()
                socket.broadcast = true
                val sendData = message.toByteArray()
                // 255.255.255.255 is the standard broadcast address
                val address = InetAddress.getByName("255.255.255.255")
                val packet = DatagramPacket(sendData, sendData.size, address, port)
                
                onLog("Sending UDP Broadcast to $port: $message")
                socket.send(packet)
                
                // Try to receive a response
                socket.soTimeout = 3000 // 3 seconds timeout
                val receiveData = ByteArray(1024)
                val receivePacket = DatagramPacket(receiveData, receiveData.size)
                
                try {
                    socket.receive(receivePacket)
                    val response = String(receivePacket.data, 0, receivePacket.length)
                    onLog("Received UDP response from ${receivePacket.address.hostAddress}: $response")
                } catch (e: SocketTimeoutException) {
                    onLog("UDP Broadcast timeout. No response.")
                }
                socket.close()
            } catch (e: Exception) {
                onLog("UDP Broadcast error: ${e.message}")
            }
        }
    }

    suspend fun sendTcpCommand(ip: String, port: Int, message: String) {
        withContext(Dispatchers.IO) {
            try {
                onLog("Connecting to TCP $ip:$port...")
                val socket = Socket(ip, port)
                socket.soTimeout = 5000
                
                val outStream = socket.getOutputStream()
                outStream.write(message.toByteArray())
                outStream.flush()
                onLog("TCP Data sent.")

                val inStream = socket.getInputStream()
                val buffer = ByteArray(1024)
                val bytesRead = inStream.read(buffer)
                
                if (bytesRead != -1) {
                    val response = String(buffer, 0, bytesRead)
                    onLog("Received TCP response: $response")
                } else {
                    onLog("TCP connection closed by peer without response.")
                }
                socket.close()
            } catch (e: Exception) {
                onLog("TCP Error: ${e.message}")
            }
        }
    }

    suspend fun sendTcpCommandBytes(ip: String, port: Int, message: ByteArray) {
        withContext(Dispatchers.IO) {
            try {
                onLog("Connecting to TCP $ip:$port to send bytes...")
                val socket = Socket(ip, port)
                socket.soTimeout = 5000
                
                val outStream = socket.getOutputStream()
                outStream.write(message)
                outStream.flush()
                onLog("TCP Bytes sent.")

                val inStream = socket.getInputStream()
                val buffer = ByteArray(1024)
                val bytesRead = inStream.read(buffer)
                
                if (bytesRead != -1) {
                    val response = buffer.copyOfRange(0, bytesRead).joinToString(" ") { String.format("%02X", it) }
                    onLog("Received TCP bytes response: $response")
                } else {
                    onLog("TCP connection closed by peer without response.")
                }
                socket.close()
            } catch (e: java.net.SocketTimeoutException) {
                onLog("TCP Read timed out (Normal behavior for MagicHome ON/OFF commands).")
            } catch (e: Exception) {
                onLog("TCP Bytes Error: ${e.message}")
            }
        }
    }

    suspend fun provisionMagicHome(ip: String, ssid: String, pass: String) {
        withContext(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.soTimeout = 2000
                socket.broadcast = true
                val address = InetAddress.getByName(ip)
                val port = 48899

                suspend fun sendCommand(cmd: String, expectResponse: Boolean = true): String? {
                    onLog("Provisioning: Sending '$cmd'")
                    val data = cmd.toByteArray()
                    val packet = DatagramPacket(data, data.size, address, port)
                    socket.send(packet)
                    if (!expectResponse) return null

                    val buffer = ByteArray(1024)
                    val recvPacket = DatagramPacket(buffer, buffer.size)
                    return try {
                        socket.receive(recvPacket)
                        val response = String(recvPacket.data, 0, recvPacket.length).trim()
                        onLog("Provisioning: Received '$response'")
                        response
                    } catch (e: java.net.SocketTimeoutException) {
                        onLog("Provisioning: Timeout waiting for response to '$cmd'")
                        null
                    }
                }

                // 1. Iniciar handshake
                val handshakeResp = sendCommand("HF-A11ASSISTHREAD")
                if (handshakeResp != null) {
                    // 2. Enviar +ok
                    sendCommand("+ok")
                    
                    // 3. Configurar modo STA
                    sendCommand("AT+WMODE=STA\r")
                    
                    // 4. Configurar SSID
                    sendCommand("AT+WSSSID=$ssid\r")
                    
                    // 5. Configurar Password
                    sendCommand("AT+WSKEY=WPA2PSK,AES,$pass\r")
                    
                    // 6. Reiniciar dispositivo para aplicar cambios
                    sendCommand("AT+Z\r", expectResponse = false)
                    
                    onLog("Provisioning complete. Device should now reboot and connect to $ssid")
                } else {
                    onLog("Provisioning failed: Handshake response not received.")
                }

            } catch (e: Exception) {
                onLog("Provisioning Error: ${e.message}")
            } finally {
                socket?.close()
            }
        }
    }

    fun scanLocalNetwork(onDeviceFound: (String, String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.soTimeout = 3000
                socket.broadcast = true
                val address = InetAddress.getByName("255.255.255.255")
                val port = 48899

                val data = "HF-A11ASSISTHREAD".toByteArray()
                val packet = DatagramPacket(data, data.size, address, port)
                
                onLog("Local Scan: Broadcasting discovery packet...")
                socket.send(packet)

                val buffer = ByteArray(1024)
                while (true) {
                    val recvPacket = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(recvPacket)
                        val response = String(recvPacket.data, 0, recvPacket.length).trim()
                        val ip = recvPacket.address.hostAddress
                        onLog("Local Scan: Received '$response' from $ip")
                        // Response is typically: IP,MAC,Model
                        if (response.contains(",")) {
                            val parts = response.split(",")
                            if (parts.size >= 2) {
                                val mac = parts[1]
                                onDeviceFound(ip ?: "Unknown", mac)
                            }
                        } else if (response.isNotEmpty() && response != "HF-A11ASSISTHREAD") {
                            onDeviceFound(ip ?: "Unknown", response)
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        onLog("Local Scan: Finished.")
                        break // Timeout means no more devices
                    }
                }
            } catch (e: Exception) {
                onLog("Local Scan Error: ${e.message}")
            } finally {
                socket?.close()
            }
        }
    }
}

