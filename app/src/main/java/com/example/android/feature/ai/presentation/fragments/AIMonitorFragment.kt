package com.example.android.feature.ai.presentation.fragments
import com.example.android.feature.ai.data.local.PrefsManager
import com.example.android.feature.ai.data.service.BackgroundCameraService
import com.example.android.feature.ai.presentation.activities.AIVisionActivity
import com.example.android.feature.ai.domain.manager.SecuenciaConfigManager
import com.example.android.feature.ai.data.state.CameraSharedState
import com.example.android.feature.ai.domain.manager.Combo
import com.example.android.feature.ai.presentation.views.OverlayView
import com.example.android.feature.ai.presentation.activities.ScheduleActivity
import com.example.android.feature.ai.presentation.activities.SecuenciaConfigActivity
import com.example.android.feature.ai.presentation.fragments.AIMonitorFragment

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.android.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import java.util.UUID

class AIMonitorFragment : Fragment() {

    private var cameraMode = 0 // 0: Frontal, 1: Trasera, 2: Wi-Fi IP
    private var ipCameraUrl = ""

    private lateinit var viewFinder: ImageView
    private lateinit var overlayView: OverlayView
    private lateinit var switchService: SwitchCompat
    private lateinit var btnInfo: ImageButton
    private lateinit var tvNoCameraInfo: TextView
    private lateinit var btnGuardarAccion: ExtendedFloatingActionButton

    // Dirty-flag: evitar setImageBitmap si el bitmap no cambió
    private var lastShownBitmap: android.graphics.Bitmap? = null

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            proceedStartCamera()
        } else {
            switchService.isChecked = false
        }
    }

    private val uiHandler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!isAdded) return

            if (CameraSharedState.isServiceRunning) {
                CameraSharedState.latestBitmap?.let { bmp ->
                    // Solo actualizar si el bitmap cambió (comparar referencia, no contenido)
                    if (bmp !== lastShownBitmap) {
                        lastShownBitmap = bmp
                        viewFinder.setImageBitmap(bmp)
                    }
                    // Espejo visual para cámara frontal (sin allocation de Bitmap)
                    viewFinder.scaleX = if (cameraMode == 0) -1f else 1f
                }

                overlayView.setMirror(cameraMode == 0)
                overlayView.updateResults(
                    CameraSharedState.lastHandResult,
                    CameraSharedState.imageWidth,
                    CameraSharedState.imageHeight
                )

                overlayView.updateAction(CameraSharedState.currentGesture)
                tvNoCameraInfo.visibility = View.GONE
            } else {
                if (lastShownBitmap != null) {
                    lastShownBitmap = null
                    viewFinder.setImageBitmap(null)
                }
                viewFinder.scaleX = 1f
                overlayView.updateResults(null, 1, 1)
                tvNoCameraInfo.visibility = View.VISIBLE
            }
            uiHandler.postDelayed(this, 33) // ~30 fps
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_ai_monitor, container, false)

        viewFinder = view.findViewById(R.id.viewFinder)
        overlayView = view.findViewById(R.id.overlayView)
        switchService = view.findViewById(R.id.switchService)
        btnInfo = view.findViewById(R.id.btnInfo)
        tvNoCameraInfo = view.findViewById(R.id.tvNoCameraInfo)
        btnGuardarAccion = view.findViewById(R.id.btnGuardarAccion)

        btnInfo.setOnClickListener {
            showInfoDialog()
        }

        btnGuardarAccion.setOnClickListener {
            guardarAccionActual()
        }

        switchService.setOnCheckedChangeListener { _, isChecked ->
            switchService.text = if (isChecked) "Encendido" else "Apagado"
            if (isChecked) {
                checkAndStartCamera()
            } else {
                stopCameraService()
            }
        }

        return view
    }

    private fun guardarAccionActual() {
        if (!CameraSharedState.isServiceRunning) {
            Snackbar.make(requireView(), "Activa el servicio para detectar un gesto", Snackbar.LENGTH_SHORT).show()
            return
        }

        val accionActual = CameraSharedState.currentGesture

        if (accionActual.isNullOrEmpty() || accionActual == "Ninguno") {
            Snackbar.make(requireView(), "No se detectó ningún gesto todavía", Snackbar.LENGTH_SHORT).show()
            return
        }

        val nuevoCombo = Combo(
            id = UUID.randomUUID().toString(),
            name = "Combo: $accionActual"
        )

        val combosActuales = SecuenciaConfigManager.loadCombos(requireContext()).toMutableList()
        combosActuales.add(nuevoCombo)
        SecuenciaConfigManager.saveCombos(requireContext(), combosActuales)

        Snackbar.make(requireView(), "Acción '$accionActual' guardada como combo", Snackbar.LENGTH_SHORT).show()

        // Como ahora todo está en AIVisionActivity, en lugar de lanzar una nueva Activity,
        // podríamos notificar al ViewPager para cambiar a la Tab de Secuencias, 
        // pero por ahora mantenemos el intent original si SecuenciaConfigActivity existe.
        // Lo ideal será pasar a la pestaña de Secuencias.
        val activity = requireActivity() as? AIVisionActivity
        // activity?.viewPager?.currentItem = 3 // Ir a la pestaña Secuencias
    }

    private fun showInfoDialog() {
        val bottomSheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_gestos_info, null)
        bottomSheet.setContentView(view)

        val rgCameraMode = view.findViewById<RadioGroup>(R.id.rgCameraMode)
        val rbCamFrontal = view.findViewById<RadioButton>(R.id.rbCamFrontal)
        val rbCamTrasera = view.findViewById<RadioButton>(R.id.rbCamTrasera)
        val rbCamWifi = view.findViewById<RadioButton>(R.id.rbCamWifi)
        val btnConfigGestos = view.findViewById<Button>(R.id.btnConfigGestos)
        val btnHorario = view.findViewById<Button>(R.id.btnHorario)

        btnHorario.text = "🕒 " + PrefsManager.getScheduleString(requireContext())

        when (cameraMode) {
            0 -> rbCamFrontal.isChecked = true
            1 -> rbCamTrasera.isChecked = true
            2 -> rbCamWifi.isChecked = true
        }

        rgCameraMode.setOnCheckedChangeListener { _, checkedId ->
            cameraMode = when (checkedId) {
                R.id.rbCamFrontal -> 0
                R.id.rbCamTrasera -> 1
                else -> 2
            }
            if (cameraMode == 2) {
                checkEspCamera()
            } else {
                if (switchService.isChecked) {
                    startCameraService()
                }
            }
            bottomSheet.dismiss()
        }

        val switchLandmarks = view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchShowLandmarks)
        val switchAction = view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchShowAction)

        switchLandmarks.isChecked = PrefsManager.isShowLandmarks(requireContext())
        switchAction.isChecked = PrefsManager.isShowAction(requireContext())

        switchLandmarks.setOnCheckedChangeListener { _, isChecked ->
            PrefsManager.setShowLandmarks(requireContext(), isChecked)
        }

        switchAction.setOnCheckedChangeListener { _, isChecked ->
            PrefsManager.setShowAction(requireContext(), isChecked)
        }

        btnConfigGestos.setOnClickListener {
            bottomSheet.dismiss()
            val activity = requireActivity()
            if (activity is AIVisionActivity) {
                val viewPager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPagerAIVision)
                viewPager?.currentItem = 1 // Tab de Gestos
            }
        }

        btnHorario.setOnClickListener {
            bottomSheet.dismiss()
            val intent = Intent(requireContext(), ScheduleActivity::class.java)
            startActivity(intent)
        }

        bottomSheet.show()
    }

    private fun checkAndStartCamera() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            proceedStartCamera()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun proceedStartCamera() {
        if (cameraMode == 2 && ipCameraUrl.isEmpty()) {
            checkEspCamera()
        } else {
            startCameraService()
        }
    }

    private fun checkEspCamera() {
        val prefs = requireContext().getSharedPreferences("EspConfigPrefs", Context.MODE_PRIVATE)
        val savedIp = prefs.getString("saved_device_ip", "")

        if (!savedIp.isNullOrEmpty()) {
            ipCameraUrl = "http://$savedIp:81/stream"
            switchService.isChecked = true
            startCameraService()
        } else {
            showIpCameraDialog()
        }
    }

    private fun showIpCameraDialog() {
        val input = android.widget.EditText(requireContext())
        input.hint = "http://192.168.1.100:81/stream"
        input.setText(ipCameraUrl)

        AlertDialog.Builder(requireContext())
            .setTitle("URL de Cámara Wi-Fi")
            .setView(input)
            .setPositiveButton("Conectar") { _, _ ->
                ipCameraUrl = input.text.toString()
                switchService.isChecked = true
                startCameraService()
            }
            .setNegativeButton("Cancelar") { _, _ ->
                if (!CameraSharedState.isServiceRunning) {
                    switchService.isChecked = false
                }
            }
            .show()
    }

    private fun startCameraService() {
        val intent = Intent(requireContext(), BackgroundCameraService::class.java).apply {
            action = BackgroundCameraService.ACTION_START
            putExtra(BackgroundCameraService.EXTRA_CAMERA_MODE, cameraMode)
            putExtra(BackgroundCameraService.EXTRA_IP_URL, ipCameraUrl)
        }
        androidx.core.content.ContextCompat.startForegroundService(requireContext(), intent)
    }

    private fun stopCameraService() {
        val intent = Intent(requireContext(), BackgroundCameraService::class.java).apply {
            action = BackgroundCameraService.ACTION_STOP
        }
        requireContext().startService(intent)
    }

    override fun onResume() {
        super.onResume()
        switchService.setOnCheckedChangeListener(null)
        switchService.isChecked = CameraSharedState.isServiceRunning
        switchService.text = if (switchService.isChecked) "Encendido" else "Apagado"
        switchService.setOnCheckedChangeListener { _, isChecked ->
            switchService.text = if (isChecked) "Encendido" else "Apagado"
            if (isChecked) {
                checkAndStartCamera()
            } else {
                stopCameraService()
            }
        }

        if (CameraSharedState.isServiceRunning) {
            val intent = Intent(requireContext(), BackgroundCameraService::class.java).apply {
                action = BackgroundCameraService.ACTION_RELOAD_COMBOS
            }
            requireContext().startService(intent)
        }
        uiHandler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(updateRunnable)
    }
}
