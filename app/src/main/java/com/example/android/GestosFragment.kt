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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
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

class GestosFragment : Fragment() {

    private var cameraMode = 0 // 0: Frontal, 1: Trasera, 2: Wi-Fi IP
    private var ipCameraUrl = ""

    private lateinit var viewFinder: ImageView
    private lateinit var overlayView: OverlayView
    private lateinit var switchService: SwitchCompat
    private lateinit var btnSchedule: Button
    private lateinit var btnSwitchCamera: ImageButton

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
            } else {
                viewFinder.setImageBitmap(null)
                overlayView.updateResults(null, null, 1, 1)
            }
            uiHandler.postDelayed(this, 33) // ~30 fps
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_gestos, container, false)
        
        viewFinder = view.findViewById(R.id.viewFinder)
        overlayView = view.findViewById(R.id.overlayView)
        switchService = view.findViewById(R.id.switchService)
        btnSchedule = view.findViewById(R.id.btnSchedule)
        btnSwitchCamera = view.findViewById(R.id.btnSwitchCamera)

        view.setOnLongClickListener {
            val intent = Intent(requireContext(), ComboListActivity::class.java)
            startActivity(intent)
            true
        }

        btnSwitchCamera.setOnClickListener {
            val options = arrayOf("Cámara Frontal", "Cámara Trasera", "Cámara Wi-Fi IP")
            AlertDialog.Builder(requireContext())
                .setTitle("Seleccionar Cámara")
                .setSingleChoiceItems(options, cameraMode) { dialog, which ->
                    cameraMode = which
                    if (cameraMode == 2) {
                        checkEspCamera()
                    } else {
                        if (switchService.isChecked) {
                            startCameraService()
                        }
                    }
                    dialog.dismiss()
                }
                .show()
        }

        switchService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkAndStartCamera()
            } else {
                stopCameraService()
            }
        }

        btnSchedule.setOnClickListener {
            showScheduleDialog()
        }

        updateScheduleButtonUI()
        
        return view
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

    private fun showScheduleDialog() {
        val options = arrayOf("Activo 24/7", "Programar Horario")
        val currentSelection = if (PrefsManager.isScheduleEnabled(requireContext())) 1 else 0

        AlertDialog.Builder(requireContext())
            .setTitle("Configurar Horario")
            .setSingleChoiceItems(options, currentSelection) { dialog, which ->
                if (which == 0) {
                    PrefsManager.setScheduleEnabled(requireContext(), false)
                    updateScheduleButtonUI()
                    notifyServiceScheduleChanged()
                    dialog.dismiss()
                } else {
                    dialog.dismiss()
                    showTimePickerForStart()
                }
            }
            .show()
    }

    private fun showTimePickerForStart() {
        val currentHour = PrefsManager.getStartHour(requireContext())
        val currentMinute = PrefsManager.getStartMinute(requireContext())
        
        TimePickerDialog(requireContext(), { _, hour, minute ->
            PrefsManager.setStartHour(requireContext(), hour)
            PrefsManager.setStartMinute(requireContext(), minute)
            showTimePickerForEnd()
        }, currentHour, currentMinute, true).apply {
            setTitle("Hora de INICIO")
            show()
        }
    }

    private fun showTimePickerForEnd() {
        val currentHour = PrefsManager.getEndHour(requireContext())
        val currentMinute = PrefsManager.getEndMinute(requireContext())
        
        TimePickerDialog(requireContext(), { _, hour, minute ->
            PrefsManager.setEndHour(requireContext(), hour)
            PrefsManager.setEndMinute(requireContext(), minute)
            PrefsManager.setScheduleEnabled(requireContext(), true)
            updateScheduleButtonUI()
            notifyServiceScheduleChanged()
        }, currentHour, currentMinute, true).apply {
            setTitle("Hora de FIN")
            show()
        }
    }

    private fun updateScheduleButtonUI() {
        btnSchedule.text = "🕒 " + PrefsManager.getScheduleString(requireContext())
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
        switchService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkAndStartCamera()
            } else {
                stopCameraService()
            }
        }
        updateScheduleButtonUI()

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
