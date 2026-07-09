package com.example.android

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.android.network.MjpegStreamReader
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class DeviceCameraActivity : AppCompatActivity() {

    private lateinit var tvCameraIp: TextView
    private lateinit var viewFinder: ImageView
    private lateinit var progressVideo: ProgressBar
    private lateinit var btnFinalizar: MaterialButton
    private lateinit var toolbarCamera: MaterialToolbar

    private var mjpegStreamReader: MjpegStreamReader? = null
    private var remoteCameraStreamReader: com.example.android.network.RemoteCameraStreamReader? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_camera)

        tvCameraIp = findViewById(R.id.tvCameraIp)
        viewFinder = findViewById(R.id.viewFinder)
        progressVideo = findViewById(R.id.progressVideo)
        btnFinalizar = findViewById(R.id.btnFinalizar)
        toolbarCamera = findViewById(R.id.toolbarCamera)

        toolbarCamera.setNavigationOnClickListener {
            finishAndGoHome()
        }

        val ip = intent.getStringExtra("EXTRA_IP") ?: ""
        val deviceKey = intent.getStringExtra("EXTRA_MAC") ?: "TEST-CAM-1" // Mock temporal si no viene
        
        tvCameraIp.text = if (ip.isNotEmpty()) ip else deviceKey

        btnFinalizar.setOnClickListener {
            finishAndGoHome()
        }

        if (ip.isNotEmpty() || deviceKey.isNotEmpty()) {
            startCameraStream(ip, deviceKey)
        } else {
            progressVideo.isVisible = false
        }
    }

    private fun startCameraStream(ip: String, deviceKey: String) {
        val usarRemoto = true // o basado en si hay conectividad LAN al IP local

        if (usarRemoto && deviceKey.isNotEmpty()) {
            remoteCameraStreamReader = com.example.android.network.RemoteCameraStreamReader(
                wsBaseUrl = "wss://tu-backend.onrender.com", // O la variable de configuración global
                deviceKey = deviceKey,
                onFrameReceived = { bitmap -> 
                    runOnUiThread { 
                        progressVideo.isVisible = false
                        viewFinder.setImageBitmap(bitmap) 
                    } 
                },
                onError = { msg -> runOnUiThread { android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show() } }
            )
            remoteCameraStreamReader?.start()
        } else if (ip.isNotEmpty()) {
            val streamUrl = "http://$ip:81/stream"
            mjpegStreamReader = MjpegStreamReader(streamUrl) { bitmap ->
                runOnUiThread {
                    progressVideo.isVisible = false
                    viewFinder.setImageBitmap(bitmap)
                }
            }
            mjpegStreamReader?.start()
        } else {
            progressVideo.isVisible = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mjpegStreamReader?.stop()
        remoteCameraStreamReader?.stop()
    }

    private fun finishAndGoHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }
}
