package com.example.android.feature.device

import com.example.android.R

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.android.core.network.stream.MjpegStreamReader
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.example.android.core.voice.VoiceCommandListener
import com.example.android.core.voice.VoiceCommandMatcher
import com.example.android.core.actions.GestureActionExecutor
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.android.feature.home.HomeActivity

class DeviceCameraActivity : AppCompatActivity() {

    private lateinit var tvCameraIp: TextView
    private lateinit var viewFinder: ImageView
    private lateinit var progressVideo: ProgressBar
    private lateinit var btnFinalizar: MaterialButton
    private lateinit var toolbarCamera: MaterialToolbar
    private lateinit var fabVoice: FloatingActionButton

    private var mjpegStreamReader: MjpegStreamReader? = null
    private var voiceListener: VoiceCommandListener? = null

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

        fabVoice = findViewById(R.id.fabVoice)
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val vozHabilitada = prefs.getBoolean("CONTROL_VOZ_ACTIVADO", false)

        if (vozHabilitada) {
            fabVoice.isVisible = true
            configurarVoz()
        }
    }

    private fun configurarVoz() {
        voiceListener = VoiceCommandListener(
            context = this,
            onResultado = { texto ->
                Toast.makeText(this, "Escuchado: $texto", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    val gestoEncontrado = VoiceCommandMatcher.encontrarGesto(this@DeviceCameraActivity, texto)
                    if (gestoEncontrado != null) {
                        GestureActionExecutor.executeVoiceAction(this@DeviceCameraActivity, gestoEncontrado)
                    } else {
                        Toast.makeText(this@DeviceCameraActivity, "Comando no reconocido", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onError = { error ->
                Toast.makeText(this, "Error de voz: $error", Toast.LENGTH_SHORT).show()
            }
        )

        fabVoice.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                voiceListener?.iniciarEscucha()
                Toast.makeText(this, "Escuchando comando de voz...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Falta permiso de micrófono", Toast.LENGTH_LONG).show()
            }
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
        voiceListener?.detener()
    }

    private fun finishAndGoHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }
}
