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
        tvCameraIp.text = if (ip.isNotEmpty()) ip else "Desconocida"

        btnFinalizar.setOnClickListener {
            finishAndGoHome()
        }

        if (ip.isNotEmpty()) {
            startCameraStream(ip)
        } else {
            progressVideo.isVisible = false
        }
    }

    private fun startCameraStream(ip: String) {
        val streamUrl = "http://$ip:81/stream"
        mjpegStreamReader = MjpegStreamReader(streamUrl) { bitmap ->
            runOnUiThread {
                progressVideo.isVisible = false
                viewFinder.setImageBitmap(bitmap)
            }
        }
        mjpegStreamReader?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        mjpegStreamReader?.stop()
    }

    private fun finishAndGoHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }
}
