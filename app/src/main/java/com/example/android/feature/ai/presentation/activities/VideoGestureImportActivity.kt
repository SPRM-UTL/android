package com.example.android.feature.ai.presentation.activities

import com.example.android.feature.ai.domain.analyzer.FingerAnalyzer
import com.example.android.feature.ai.domain.analyzer.GestureClassifier
import com.example.android.feature.ai.domain.analyzer.HandNormalizer
import com.example.android.feature.ai.domain.analyzer.ManoObjetivo
import com.example.android.feature.ai.domain.analyzer.PalmAnalyzer
import com.example.android.feature.ai.domain.analyzer.PasoSecuencia
import com.example.android.R

import android.app.Activity
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.framework.image.BitmapImageBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Permite importar gestos de mano desde un video local.
 *
 * Flujo:
 * 1. El usuario elige un video con [btnSelectVideo].
 * 2. Al pulsar [btnAnalyze] se extraen frames con [MediaMetadataRetriever] cada ~66ms (~15fps).
 * 3. Cada frame se procesa con HandLandmarker en RunningMode.VIDEO (síncrono/offline).
 * 4. La secuencia de poses se agrupa: grupos cortos se descartan como ruido.
 * 5. Los grupos restantes se convierten en [PasoSecuencia] y se devuelven a [SecuenciaConfigActivity]
 *    para que el usuario los revise/edite antes de guardar.
 */
class VideoGestureImportActivity : AppCompatActivity() {

    private lateinit var videoPreview: VideoView
    private lateinit var layoutNoVideo: View
    private lateinit var btnSelectVideo: MaterialButton
    private lateinit var btnAnalyze: MaterialButton
    private lateinit var btnUsarPasos: MaterialButton
    private lateinit var tvStatus: TextView
    private lateinit var tvPasosDetectados: TextView
    private lateinit var progressAnalysis: ProgressBar

    private var selectedVideoUri: Uri? = null
    private val detectedSteps = mutableListOf<PasoSecuencia>()

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        /** Frame interval en microsegundos para muestreo a ~15 fps. */
        private const val FRAME_INTERVAL_US = 66_666L
        /** Mínimo de frames consecutivos para considerar un grupo como paso válido (anti-ruido). */
        private const val MIN_FRAMES_PER_GROUP = 3
        /** Cuadros base que se asigna a cada paso detectado (escala igual al wizard manual). */
        private const val DEFAULT_FRAMES_PER_STEP = 15

        /** Key para la lista de pasos serializada que se devuelve como resultado. */
        const val EXTRA_PASOS_JSON = "EXTRA_PASOS_JSON"
        const val EXTRA_HAND_JSON = "EXTRA_HAND_JSON"
    }

    // Selector de video desde la galería/documentos
    private val videoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        selectedVideoUri = uri
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        videoPreview.setVideoURI(uri)
        videoPreview.visibility = View.VISIBLE
        layoutNoVideo.visibility = View.GONE
        btnAnalyze.isEnabled = true
        tvStatus.text = "Video listo. Pulsa Analizar."
        tvStatus.visibility = View.VISIBLE
        detectedSteps.clear()
        btnUsarPasos.visibility = View.GONE
        tvPasosDetectados.visibility = View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_video_gesture_import)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainVideoImport)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            val cardBack = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardBack)
            cardBack?.getChildAt(0)?.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        videoPreview = findViewById(R.id.videoPreview)
        layoutNoVideo = findViewById(R.id.layoutNoVideo)
        btnSelectVideo = findViewById(R.id.btnSelectVideo)
        btnAnalyze = findViewById(R.id.btnAnalyze)
        btnUsarPasos = findViewById(R.id.btnUsarPasos)
        tvStatus = findViewById(R.id.tvStatus)
        tvPasosDetectados = findViewById(R.id.tvPasosDetectados)
        progressAnalysis = findViewById(R.id.progressAnalysis)

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        btnSelectVideo.setOnClickListener {
            videoPickerLauncher.launch(arrayOf("video/*"))
        }

        btnAnalyze.setOnClickListener {
            val uri = selectedVideoUri ?: return@setOnClickListener
            analyzeVideo(uri)
        }

        btnUsarPasos.setOnClickListener {
            if (detectedSteps.isEmpty()) return@setOnClickListener
            // Devolver los pasos como JSON simplificado al caller
            val resultIntent = Intent().apply {
                // Serializar pasos: "nombreGesto|mano|frames" separados por '\n'
                val pasosStr = detectedSteps.joinToString("\n") {
                    "${it.nombreGesto}|${it.manoObjetivo.name}|${it.cuadrosRequeridos}"
                }
                putExtra(EXTRA_PASOS_JSON, pasosStr)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun analyzeVideo(uri: Uri) {
        setUiAnalyzing(true)
        activityScope.launch {
            try {
                val steps = withContext(Dispatchers.IO) {
                    extractAndClassify(uri) { progress ->
                        // Actualizar progreso: este lambda se llama desde IO,
                        // usamos withContext para saltar al Main sin bloquear
                        activityScope.launch(Dispatchers.Main) {
                            progressAnalysis.progress = progress
                        }
                    }
                }
                detectedSteps.clear()
                detectedSteps.addAll(steps)
                displayResults(steps)
            } catch (e: Exception) {
                tvStatus.text = "Error durante el análisis: ${e.message}"
            } finally {
                setUiAnalyzing(false)
            }
        }
    }

    /**
     * Extrae frames del video y clasifica la pose de mano en cada frame.
     * Corre en IO para no bloquear el hilo principal.
     * @param onProgress callback llamado con el porcentaje (0-100) cada frame procesado.
     */
    private fun extractAndClassify(uri: Uri, onProgress: (Int) -> Unit): List<PasoSecuencia> {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(this, uri)

        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: return emptyList()
        val durationUs = durationMs * 1000L

        // Construir HandLandmarker en modo VIDEO (síncrono, no requiere ResultListener)
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .setDelegate(com.google.mediapipe.tasks.core.Delegate.CPU) // CPU para procesamiento offline
            .build()
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.VIDEO)
            .setNumHands(2)
            .build()
        val landmarker = HandLandmarker.createFromOptions(this, options)

        // Lista de (timestampMs, pose, mano)
        data class FramePose(val pose: String, val hand: String)
        val frameResults = mutableListOf<FramePose>()

        var timestampUs = 0L
        val totalFrames = (durationUs / FRAME_INTERVAL_US).toInt().coerceAtLeast(1)
        var frameIdx = 0

        while (timestampUs <= durationUs) {
            val bitmap = try {
                retriever.getFrameAtTime(timestampUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (e: Exception) { null }

            if (bitmap != null) {
                val mpImage = BitmapImageBuilder(bitmap).build()
                val result = landmarker.detectForVideo(mpImage, timestampUs / 1000)

                val landmarks = result?.landmarks()
                val handednesses = result?.handednesses()

                if (landmarks != null && handednesses != null && landmarks.isNotEmpty()) {
                    val hand = landmarks[0]
                    val handedness = handednesses[0].firstOrNull()?.categoryName() ?: "Right"
                    val localLandmarks = HandNormalizer.toLocalCoordinates(hand)
                    val fingers = FingerAnalyzer.analyzeFingers(localLandmarks)
                    val palm = PalmAnalyzer.analyzePalm(localLandmarks, handedness, isFrontCamera = false)
                    val pose = GestureClassifier.classify(fingers, palm)
                    if (pose.name != "DESCONOCIDO") {
                        val isFrente = palm.isFacingCamera
                        val vistaText = if (isFrente) "Frente" else "Espalda"
                        frameResults.add(FramePose("${pose.name.replace("_", " ")} [$vistaText]", handedness))
                    } else {
                        frameResults.add(FramePose("", ""))
                    }
                } else {
                    frameResults.add(FramePose("", ""))
                }
            }

            timestampUs += FRAME_INTERVAL_US
            frameIdx++
            // Actualizar progreso en el hilo principal
            val progress = (frameIdx * 100 / totalFrames).coerceIn(0, 100)
            onProgress(progress)
        }

        retriever.release()
        landmarker.close()

        return groupFramesToPasos(frameResults.map { it.pose to it.hand })
    }

    /**
     * Agrupa frames consecutivos con la misma pose en candidatos de [PasoSecuencia].
     * Descarta grupos con menos de [MIN_FRAMES_PER_GROUP] frames (ruido).
     */
    private fun groupFramesToPasos(frames: List<Pair<String, String>>): List<PasoSecuencia> {
        if (frames.isEmpty()) return emptyList()

        val pasos = mutableListOf<PasoSecuencia>()
        var currentPose = frames[0].first
        var currentHand = frames[0].second
        var count = 1

        for (i in 1 until frames.size) {
            val (pose, hand) = frames[i]
            if (pose == currentPose) {
                count++
            } else {
                // Fin del grupo actual
                if (currentPose.isNotBlank() && count >= MIN_FRAMES_PER_GROUP) {
                    val mano = when (currentHand) {
                        "Left" -> ManoObjetivo.LEFT
                        "Right" -> ManoObjetivo.RIGHT
                        else -> ManoObjetivo.ANY
                    }
                    pasos.add(PasoSecuencia(currentPose, mano, DEFAULT_FRAMES_PER_STEP))
                }
                currentPose = pose
                currentHand = hand
                count = 1
            }
        }
        // Último grupo
        if (currentPose.isNotBlank() && count >= MIN_FRAMES_PER_GROUP) {
            val mano = when (currentHand) {
                "Left" -> ManoObjetivo.LEFT
                "Right" -> ManoObjetivo.RIGHT
                else -> ManoObjetivo.ANY
            }
            pasos.add(PasoSecuencia(currentPose, mano, DEFAULT_FRAMES_PER_STEP))
        }
        return pasos
    }

    private fun displayResults(steps: List<PasoSecuencia>) {
        if (steps.isEmpty()) {
            tvStatus.text = "No se detectaron gestos válidos en el video."
            tvPasosDetectados.visibility = View.GONE
            btnUsarPasos.visibility = View.GONE
        } else {
            tvStatus.text = "Se detectaron ${steps.size} paso(s). Revísalos antes de guardar:"
            val sb = StringBuilder()
            steps.forEachIndexed { i, paso ->
                sb.appendLine("${i + 1}. ${paso.nombreGesto} · ${paso.manoObjetivo.name} · ${paso.cuadrosRequeridos} cuadros")
            }
            tvPasosDetectados.text = sb.toString().trim()
            tvPasosDetectados.visibility = View.VISIBLE
            btnUsarPasos.visibility = View.VISIBLE
        }
        tvStatus.visibility = View.VISIBLE
    }

    private fun setUiAnalyzing(analyzing: Boolean) {
        btnAnalyze.isEnabled = !analyzing
        btnSelectVideo.isEnabled = !analyzing
        progressAnalysis.visibility = if (analyzing) View.VISIBLE else View.GONE
        progressAnalysis.progress = 0
        if (analyzing) {
            tvStatus.text = "Analizando video..."
            tvStatus.visibility = View.VISIBLE
            btnUsarPasos.visibility = View.GONE
            tvPasosDetectados.visibility = View.GONE
        }
    }
}
