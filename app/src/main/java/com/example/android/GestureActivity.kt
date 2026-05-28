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
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.android.view.Snackbars
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.io.File
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

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private lateinit var vistaRaiz: View
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Snackbars.info(vistaRaiz, "Se requiere permiso de cámara para esta función", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gesture)
        vistaRaiz = findViewById(android.R.id.content)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor = android.graphics.Color.TRANSPARENT

        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = false


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

            val tiempoPreparacionMs = 3000L
            pbContadorLineal.max = tiempoPreparacionMs.toInt()

            object : CountDownTimer(tiempoPreparacionMs, 20) {
                override fun onTick(millisUntilFinished: Long) {
                    val tiempoTranscurrido = tiempoPreparacionMs - millisUntilFinished
                    pbContadorLineal.progress = tiempoTranscurrido.toInt()

                    val porcentajeCompletado = (tiempoTranscurrido.toFloat() / tiempoPreparacionMs.toFloat()) * 100
                    pbContadorCircular.progress = porcentajeCompletado.toInt()

                    val segundosRestantes = (millisUntilFinished / 1000) + 1
                    tvContador.text = segundosRestantes.toString()
                }

                override fun onFinish() {
                    tvContador.text = "Ya!"
                    pbContadorLineal.progress = tiempoPreparacionMs.toInt()
                    pbContadorCircular.progress = 100

                    tvContador.postDelayed({
                        tvContador.text = ""
                        flContadorCircular.visibility = View.INVISIBLE

                        startVideoRecording()
                    }, 600)
                }
            }.start()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.SD))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, videoCapture
                )

            } catch(exc: Exception) {
                Snackbars.info(vistaRaiz, "Error al configurar los componentes de la cámara", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun startVideoRecording() {
        val videoCaptureLocal = videoCapture ?: return

        // Solo creamos un archivo temporal para guardar el video
        val archivoVideo = File(cacheDir, "captura_gesto.mp4")
        val outputOptions = FileOutputOptions.Builder(archivoVideo).build()

        recording = videoCaptureLocal.output
            .prepareRecording(this, outputOptions)
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        val duracionGrabacionMs = 5000L
                        pbContadorLineal.max = duracionGrabacionMs.toInt()
                        pbContadorLineal.progress = 0
                        pbContadorLineal.visibility = View.VISIBLE

                        iniciarTemporizadorBarraGrabacion(duracionGrabacionMs)
                    }
                    is VideoRecordEvent.Finalize -> {
                        pbContadorLineal.visibility = View.INVISIBLE
                        btnIniciarContador.isEnabled = true

                        if (!recordEvent.hasError()) {
                            Snackbars.info(vistaRaiz, "Video guardado con éxito", Toast.LENGTH_SHORT).show()
                            // Aqui seguira la logica para enviar el video al back o algun analizador
                        } else {
                            Snackbars.info(vistaRaiz, "Error en la captura del archivo", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
    }

    private fun iniciarTemporizadorBarraGrabacion(duracionMs: Long) {
        object : CountDownTimer(duracionMs, 20) {
            override fun onTick(millisUntilFinished: Long) {
                val transcurrido = duracionMs - millisUntilFinished
                pbContadorLineal.progress = transcurrido.toInt()
            }

            override fun onFinish() {
                pbContadorLineal.progress = duracionMs.toInt()
                stopVideoRecording()
            }
        }.start()
    }

    private fun stopVideoRecording() {
        recording?.stop()
        recording = null
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}