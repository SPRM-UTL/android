package com.example.android.feature.ai.presentation.activities

import com.example.android.feature.ai.domain.analyzer.PasoSecuencia
import com.example.android.feature.ai.domain.analyzer.ManoObjetivo
import com.example.android.feature.ai.domain.analyzer.HandNormalizer
import com.example.android.feature.ai.domain.analyzer.FingerAnalyzer
import com.example.android.feature.ai.domain.analyzer.PalmAnalyzer
import com.example.android.feature.ai.domain.analyzer.GestureClassifier
import com.example.android.feature.ai.domain.models.HandPose
import com.example.android.R

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class GrabarSecuenciaActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var tvGestureOverlay: TextView
    private lateinit var tvRecordStatus: TextView
    private lateinit var tvStepsHeader: TextView
    private lateinit var rvCapturedSteps: RecyclerView
    private lateinit var fabRecord: FloatingActionButton
    private lateinit var btnUseSteps: MaterialButton

    private lateinit var cameraExecutor: ExecutorService
    private var handLandmarker: HandLandmarker? = null

    private var isRecording = false
    private val capturedSteps = mutableListOf<PasoSecuencia>()
    private lateinit var stepsAdapter: GrabarStepAdapter

    private var lastCapturedPose: String = ""
    private var lastCaptureTime: Long = 0L
    private val COOLDOWN_MS = 1500L

    private val landmarkSmoother = com.example.android.feature.ai.domain.utils.HandLandmarkSmoother()
    private val leftStabilizer = com.example.android.feature.ai.domain.utils.HandPoseStabilizer(framesToConfirm = 2, emptyFramesToClear = 3)
    private val rightStabilizer = com.example.android.feature.ai.domain.utils.HandPoseStabilizer(framesToConfirm = 2, emptyFramesToClear = 3)

    private var bitmapBuffer: Bitmap? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Se requiere permiso de cámara para esta función", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    companion object {
        const val EXTRA_PASOS_JSON = "EXTRA_PASOS_JSON"
        private const val MEDIAPIPE_MAX_WIDTH = 320
        private const val MEDIAPIPE_MAX_HEIGHT = 240
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.WHITE
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        setContentView(R.layout.activity_grabar_secuencia)

        previewView = findViewById(R.id.previewView)
        tvGestureOverlay = findViewById(R.id.tvGestureOverlay)
        tvRecordStatus = findViewById(R.id.tvRecordStatus)
        tvStepsHeader = findViewById(R.id.tvStepsHeader)
        rvCapturedSteps = findViewById(R.id.rvCapturedSteps)
        fabRecord = findViewById(R.id.fabRecord)
        btnUseSteps = findViewById(R.id.btnUseSteps)

        cameraExecutor = Executors.newSingleThreadExecutor()

        stepsAdapter = GrabarStepAdapter(capturedSteps)
        rvCapturedSteps.layoutManager = LinearLayoutManager(this)
        rvCapturedSteps.adapter = stepsAdapter

        setupInsets()
        setupMediaPipe()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        fabRecord.setOnClickListener { toggleRecording() }
        btnUseSteps.setOnClickListener { returnSteps() }
    }

    private fun setupInsets() {
        val root = findViewById<View>(R.id.mainGrabarSecuencia)
        val cardBack = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardBack)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            cardBack?.getChildAt(0)?.setPadding(0, systemBars.top, 0, 0)
            root.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupMediaPipe() {
        try {
            val handBaseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .setDelegate(Delegate.GPU)
                .build()
            val handOptions = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(handBaseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(2)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result, _ ->
                    processHandResult(result)
                }
                .setErrorListener { error ->
                    Log.e("GrabarSecuencia", "MediaPipe error: ${error.message}")
                }
                .build()
            handLandmarker = HandLandmarker.createFromOptions(this, handOptions)
        } catch (e: Exception) {
            Log.e("GrabarSecuencia", "Error setting up MediaPipe", e)
            Toast.makeText(this, "Error al inicializar detección de manos", Toast.LENGTH_LONG).show()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // 1. Configuración de Preview para visualizar la cámara en el PreviewView
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // 2. Configuración del formateador de ImageAnalysis
            val resolutionSelector = androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                .setResolutionStrategy(
                    androidx.camera.core.resolutionselector.ResolutionStrategy(
                        Size(480, 640),
                        androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(resolutionSelector)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                // Vincular AMBOS use-cases: Preview e ImageAnalysis
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (e: Exception) {
                Log.e("GrabarSecuencia", "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap() ?: run {
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        imageProxy.close()

        val displayBitmap = if (rotationDegrees != 0 || true) {
            val matrix = Matrix().apply {
                if (rotationDegrees != 0) postRotate(rotationDegrees.toFloat())
                postScale(-1f, 1f) // Espejo horizontal para frontal
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
        } else {
            bitmap
        }

        val mediaPipeBitmap = if (displayBitmap.width > MEDIAPIPE_MAX_WIDTH || displayBitmap.height > MEDIAPIPE_MAX_HEIGHT) {
            val scale = minOf(
                MEDIAPIPE_MAX_WIDTH.toFloat() / displayBitmap.width,
                MEDIAPIPE_MAX_HEIGHT.toFloat() / displayBitmap.height
            )
            Bitmap.createScaledBitmap(displayBitmap, (displayBitmap.width * scale).toInt(), (displayBitmap.height * scale).toInt(), true)
        } else {
            displayBitmap
        }

        val mpImage = BitmapImageBuilder(mediaPipeBitmap).build()
        val timestamp = System.currentTimeMillis()
        val processingOptions = com.google.mediapipe.tasks.vision.core.ImageProcessingOptions.builder().build()

        try {
            handLandmarker?.detectAsync(mpImage, processingOptions, timestamp)
        } catch (e: Exception) {
            Log.e("GrabarSecuencia", "detectAsync error: ${e.message}")
        }
    }

    private fun processHandResult(result: HandLandmarkerResult) {
        val handLandmarks = result.landmarks()
        val handednesses = result.handednesses()

        var leftPose = ""
        var rightPose = ""

        if (handLandmarks != null && handednesses != null && handLandmarks.isNotEmpty()) {
            for (i in handLandmarks.indices) {
                val hand = handLandmarks[i]
                val handednessCategory = handednesses[i].first()
                val handedness = handednessCategory.categoryName()
                val handScore = handednessCategory.score()

                if (handScore < 0.6f) continue

                val smoothedHand = landmarkSmoother.smooth(hand, handedness)
                val localLandmarks = HandNormalizer.toLocalCoordinates(smoothedHand)
                val fingers = FingerAnalyzer.analyzeFingers(localLandmarks)
                val palm = PalmAnalyzer.analyzePalm(localLandmarks, handedness, true)

                val pose = GestureClassifier.classify(fingers, palm)
                if (pose == HandPose.DESCONOCIDO) continue

                val baseName = pose.name.replace("_", " ")
                val nombreGesto = baseName

                val realHandedness = if (handedness == "Left") "Right" else "Left"

                if (realHandedness == "Right") {
                    rightPose = nombreGesto
                } else if (realHandedness == "Left") {
                    leftPose = nombreGesto
                }
            }
        }

        val smoothedLeft = leftStabilizer.update(leftPose)
        val smoothedRight = rightStabilizer.update(rightPose)

        if (isRecording) {
            val now = System.currentTimeMillis()
            val cooldownOk = (now - lastCaptureTime) >= COOLDOWN_MS

            if (smoothedLeft.isNotEmpty() && smoothedLeft != lastCapturedPose && cooldownOk) {
                addCapturedStep(smoothedLeft, ManoObjetivo.LEFT)
                lastCapturedPose = smoothedLeft
                lastCaptureTime = now
            } else if (smoothedRight.isNotEmpty() && smoothedRight != lastCapturedPose && cooldownOk) {
                addCapturedStep(smoothedRight, ManoObjetivo.RIGHT)
                lastCapturedPose = smoothedRight
                lastCaptureTime = now
            }

            if (smoothedLeft.isEmpty() && smoothedRight.isEmpty()) {
                lastCapturedPose = ""
            }
        }

        val gestureText = buildString {
            if (smoothedLeft.isNotEmpty()) append("Izq: $smoothedLeft")
            if (smoothedLeft.isNotEmpty() && smoothedRight.isNotEmpty()) append(", ")
            if (smoothedRight.isNotEmpty()) append("Der: $smoothedRight")
        }.ifEmpty { "Ninguno" }

        runOnUiThread {
            tvGestureOverlay.text = gestureText
        }
    }

    private fun addCapturedStep(nombreGesto: String, mano: ManoObjetivo) {
        val step = PasoSecuencia(nombreGesto, mano, 15)
        capturedSteps.add(step)

        runOnUiThread {
            stepsAdapter.notifyItemInserted(capturedSteps.size - 1)
            tvStepsHeader.text = "Pasos capturados (${capturedSteps.size})"
            btnUseSteps.visibility = View.VISIBLE
            rvCapturedSteps.scrollToPosition(capturedSteps.size - 1)

            val beep = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80)
            beep.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 100)
            beep.release()
        }
    }

    private fun toggleRecording() {
        isRecording = !isRecording
        if (isRecording) {
            fabRecord.setImageResource(android.R.drawable.ic_media_pause)
            // Mantener el color teal_primary con tono más oscuro o igual al grabar
            fabRecord.backgroundTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(this, R.color.teal_primary)
            )
            tvRecordStatus.text = "Grabando..."
            tvRecordStatus.setTextColor(android.graphics.Color.WHITE)
            lastCapturedPose = ""
            lastCaptureTime = 0L
        } else {
            fabRecord.setImageResource(R.drawable.lucide_circle_dot)
            fabRecord.backgroundTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(this, R.color.teal_primary)
            )
            tvRecordStatus.text = "Pausado"
            tvRecordStatus.setTextColor(android.graphics.Color.parseColor("#94A3B8"))
        }
    }

    private fun returnSteps() {
        if (capturedSteps.isEmpty()) {
            Toast.makeText(this, "No hay pasos capturados", Toast.LENGTH_SHORT).show()
            return
        }
        val pasosJson = capturedSteps.joinToString("\n") { step ->
            "${step.nombreGesto}|${step.manoObjetivo}|${step.cuadrosRequeridos}"
        }
        val resultIntent = Intent().apply {
            putExtra(EXTRA_PASOS_JSON, pasosJson)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handLandmarker?.close()
        cameraExecutor.shutdown()
    }
}

class GrabarStepAdapter(
    private val pasos: List<PasoSecuencia>
) : RecyclerView.Adapter<GrabarStepAdapter.StepViewHolder>() {

    inner class StepViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvStepNumber: TextView = itemView.findViewById(R.id.tvStepNumber)
        val tvPoseName: TextView = itemView.findViewById(R.id.tvStepPose)
        val tvStepHand: TextView = itemView.findViewById(R.id.tvStepHand)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): StepViewHolder {
        val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_grabado_step, parent, false)
        return StepViewHolder(view)
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        val step = pasos[position]
        holder.tvStepNumber.text = "${position + 1}."
        holder.tvPoseName.text = step.nombreGesto
        holder.tvStepHand.text = when (step.manoObjetivo) {
            ManoObjetivo.LEFT -> "Mano Izquierda"
            ManoObjetivo.RIGHT -> "Mano Derecha"
            ManoObjetivo.ANY -> "Cualquier Mano"
        }
    }

    override fun getItemCount(): Int = pasos.size
}