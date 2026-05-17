package com.example.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class GestureActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var tvContador: TextView
    private lateinit var pbContadorLineal: ProgressBar
    private lateinit var pbContadorCircular: CircularProgressIndicator
    private lateinit var flContadorCircular: FrameLayout
    private lateinit var btnIniciarContador: Button

    private lateinit var cameraExecutor: ExecutorService

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Se requiere permiso de cámara para esta función", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gesture)

        viewFinder = findViewById(R.id.viewFinder)
        tvContador = findViewById(R.id.tvContador)
        pbContadorLineal = findViewById(R.id.pbContadorLineal)
        pbContadorCircular = findViewById(R.id.pbContadorCircular)
        flContadorCircular = findViewById(R.id.flContadorCircular)
        btnIniciarContador = findViewById(R.id.btnIniciarContador)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        btnIniciarContador.setOnClickListener {
            btnIniciarContador.isEnabled = false

            pbContadorLineal.visibility = View.VISIBLE
            flContadorCircular.visibility = View.VISIBLE

            pbContadorLineal.progress = 0
            pbContadorCircular.progress = 0

            val tiempoTotalMs = 3000L

            object : CountDownTimer(tiempoTotalMs, 20) {
                override fun onTick(millisUntilFinished: Long) {
                    val tiempoTranscurrido = tiempoTotalMs - millisUntilFinished
                    pbContadorLineal.progress = tiempoTranscurrido.toInt()

                    val porcentajeCompletado = (tiempoTranscurrido.toFloat() / tiempoTotalMs.toFloat()) * 100
                    pbContadorCircular.progress = porcentajeCompletado.toInt()

                    val segundosRestantes = (millisUntilFinished / 1000) + 1
                    tvContador.text = segundosRestantes.toString()
                }

                override fun onFinish() {
                    tvContador.text = "Ya!"
                    pbContadorLineal.progress = 3000
                    pbContadorCircular.progress = 100

                    tvContador.postDelayed({
                        tvContador.text = ""
                        pbContadorLineal.visibility = View.INVISIBLE
                        flContadorCircular.visibility = View.INVISIBLE
                        btnIniciarContador.isEnabled = true

                        Toast.makeText(this@GestureActivity, "Iniciando detección de gestos...", Toast.LENGTH_SHORT).show()

                        // Aqui ira la logica para detectar los gestos o movimientos

                    }, 800)
                }
            }.start()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview
                )

            } catch(exc: Exception) {
                Toast.makeText(this, "Ocurrió un error al abrir la cámara frontal", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}