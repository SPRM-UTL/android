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
import androidx.lifecycle.lifecycleScope
import com.example.android.ai.CameraSharedState
import com.example.android.ai.MjpegStreamReader
import com.example.android.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            val dispositivoId = intent.getIntExtra("dispositivo_id", -1)
            if (dispositivoId != -1) {
                lifecycleScope.launch {
                    val db = AppDatabase.getDatabase(this@CameraStreamActivity)
                    val dispositivo = withContext(Dispatchers.IO) {
                        db.dispositivoDao().getAllDispositivosOnce().find { it.id == dispositivoId }
                    }
                    val savedIp = dispositivo?.ipAddress
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
                        runOnUiThread {
                            tvConnecting.text = "IP de la cámara no configurada o no sincronizada"
                            Toast.makeText(this@CameraStreamActivity, "La cámara no está configurada o no ha reportado su IP.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                tvConnecting.text = "Error al obtener cámara"
                Toast.makeText(this, "Dispositivo no especificado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        uiHandler.removeCallbacks(updateRunnable)
        mjpegStreamReader?.stop()
    }
}
