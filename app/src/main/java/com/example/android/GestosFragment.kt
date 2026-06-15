package com.example.android

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
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
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.android.ai.BackgroundCameraService
import com.example.android.ai.CameraSharedState
import com.example.android.ai.ComboListActivity
import com.example.android.ai.OverlayView
import com.example.android.ai.PrefsManager
import com.example.android.ai.ScheduleActivity

class GestosFragment : Fragment() {

    private var cameraMode = 0 // 0: Frontal, 1: Trasera, 2: Wi-Fi IP
    private var ipCameraUrl = ""

    private lateinit var viewFinder: ImageView
    private lateinit var overlayView: OverlayView
    private lateinit var switchService: SwitchCompat
    private lateinit var btnInfo: ImageButton
    private lateinit var tvNoCameraInfo: android.widget.TextView

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
                    viewFinder.setImageBitmap(bmp)
                }

                overlayView.updateResults(
                    CameraSharedState.lastPoseResult,
                    CameraSharedState.lastHandResult,
                    CameraSharedState.imageWidth,
                    CameraSharedState.imageHeight
                )
                
                overlayView.updateAction(CameraSharedState.currentAction)
                tvNoCameraInfo.visibility = View.GONE
            } else {
                viewFinder.setImageBitmap(null)
                overlayView.updateResults(null, null, 1, 1)
                tvNoCameraInfo.visibility = View.VISIBLE
            }
            uiHandler.postDelayed(this, 33) // ~30 fps
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_gestos, container, false)
        
        viewFinder = view.findViewById(R.id.viewFinder)
        overlayView = view.findViewById(R.id.overlayView)
        switchService = view.findViewById(R.id.switchService)
        btnInfo = view.findViewById(R.id.btnInfo)
        tvNoCameraInfo = view.findViewById(R.id.tvNoCameraInfo)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom + (90 * resources.displayMetrics.density).toInt())
            insets
        }

        btnInfo.setOnClickListener {
            showInfoDialog()
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

        btnConfigGestos.setOnClickListener {
            bottomSheet.dismiss()
            val intent = Intent(requireContext(), ComboListActivity::class.java)
            startActivity(intent)
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

    private fun notifyServiceScheduleChanged() {
        if (CameraSharedState.isServiceRunning) {
            val intent = Intent(requireContext(), BackgroundCameraService::class.java).apply {
                action = BackgroundCameraService.ACTION_UPDATE_SCHEDULE
            }
            requireContext().startService(intent)
        }
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
