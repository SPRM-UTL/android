package com.example.android

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.android.ai.CameraSharedState
import com.example.android.ai.MjpegStreamReader

class CameraStreamActivity : AppCompatActivity() {

    private lateinit var viewFinder: ImageView
    private lateinit var btnClose: ImageButton
    private lateinit var tvConnecting: TextView
    private var mjpegStreamReader: MjpegStreamReader? = null
    
    private val uiHandler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (CameraSharedState.isServiceRunning) {
                CameraSharedState.latestBitmap?.let { bmp ->
                    tvConnecting.visibility = View.GONE
                    viewFinder.setImageBitmap(bmp)
                }
            }
            uiHandler.postDelayed(this, 33)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_stream)
        
        viewFinder = findViewById(R.id.viewFinder)
        btnClose = findViewById(R.id.btnClose)
        tvConnecting = findViewById(R.id.tvConnecting)
        
        btnClose.setOnClickListener { finish() }
        
        if (CameraSharedState.isServiceRunning) {
            uiHandler.post(updateRunnable)
        } else {
            val prefs = getSharedPreferences("EspConfigPrefs", Context.MODE_PRIVATE)
            val savedIp = prefs.getString("saved_device_ip", "")
            if (!savedIp.isNullOrEmpty()) {
                val ipCameraUrl = "http://$savedIp:81/stream"
                mjpegStreamReader = MjpegStreamReader(ipCameraUrl) { bmp ->
                    runOnUiThread { 
                        tvConnecting.visibility = View.GONE
                        viewFinder.setImageBitmap(bmp) 
                    }
                }
                mjpegStreamReader?.start()
            } else {
                tvConnecting.text = "IP de la cámara no configurada"
                Toast.makeText(this, "La cámara no está configurada.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        uiHandler.removeCallbacks(updateRunnable)
        mjpegStreamReader?.stop()
    }
}
