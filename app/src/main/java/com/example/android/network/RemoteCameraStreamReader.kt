package com.example.android.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.Response
import okio.ByteString

class RemoteCameraStreamReader(
    private val wsBaseUrl: String,   // ej: wss://tu-backend.onrender.com
    private val deviceKey: String,
    private val onFrameReceived: (Bitmap) -> Unit,
    private val onError: (String) -> Unit = {}
) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()

    fun start() {
        val request = Request.Builder()
            .url("$wsBaseUrl/ws/camera/view/$deviceKey")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                android.util.Log.i("RemoteCamera", "Conectado a $deviceKey")
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val bytesArray = bytes.toByteArray()
                val bitmap = BitmapFactory.decodeByteArray(bytesArray, 0, bytesArray.size)
                if (bitmap != null) onFrameReceived(bitmap)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onError(t.message ?: "Error desconocido")
            }
        })
    }

    fun stop() {
        webSocket?.close(1000, "Cierre normal")
        webSocket = null
    }
}
