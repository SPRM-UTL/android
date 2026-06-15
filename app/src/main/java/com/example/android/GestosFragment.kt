package com.example.android

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import android.widget.ImageView
import android.widget.TextView
import android.widget.ImageButton
import android.os.Handler
import android.os.Looper
import android.content.Intent
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.android.db.AppDatabase
import com.example.android.db.Dispositivo
import com.example.android.db.Gesto
import com.example.android.network.ApiHandler
import com.example.android.network.RetrofitClient
import com.example.android.ui.GestureAdapter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.android.ai.CameraSharedState
import com.example.android.ai.BackgroundCameraService
import com.example.android.ai.OverlayView
import com.google.android.material.switchmaterial.SwitchMaterial

class GestosFragment : Fragment() {

    private lateinit var gestureAdapter: GestureAdapter
    private lateinit var db: AppDatabase
    private var dispositivosLocales: List<Dispositivo> = emptyList()

    private var cameraMode = 0 // 0: Frontal, 1: Trasera, 2: Wi-Fi IP
    private var ipCameraUrl = ""
    private val uiHandler = Handler(Looper.getMainLooper())
    private lateinit var overlayView: OverlayView
    private lateinit var viewFinder: ImageView
    private lateinit var tvCurrentAction: TextView
    private lateinit var switchCameraService: SwitchMaterial

    private val updateRunnable = object : Runnable {
        override fun run() {
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
                
                tvCurrentAction.text = CameraSharedState.currentAction
            } else {
                viewFinder.setImageBitmap(null)
                overlayView.updateResults(null, null, 1, 1)
                tvCurrentAction.text = "Ninguno"
            }
            uiHandler.postDelayed(this, 33) // ~30 fps
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_gestos, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getDatabase(requireContext())

        val mainGestos = view.findViewById<MotionLayout>(R.id.mainGestos)
        ViewCompat.setOnApplyWindowInsetsListener(mainGestos) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        mainGestos.post {
            mainGestos.transitionToEnd()
        }

        setupRecyclerView(view)

        view.findViewById<FloatingActionButton>(R.id.fabAddGesture).setOnClickListener {
            showGestureDialog(null)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            db.dispositivoDao().getAllDispositivos().collectLatest { dispositivos ->
                dispositivosLocales = dispositivos
                gestureAdapter.notifyDataSetChanged()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            db.gestoDao().getAllGestos().collectLatest { gestos ->
                gestureAdapter.submitList(gestos)
            }
        }

        syncGestosFromApi()
        
        setupAIControls(view)
    }

    private fun setupAIControls(view: View) {
        overlayView = view.findViewById(R.id.overlayView)
        viewFinder = view.findViewById(R.id.viewFinder)
        tvCurrentAction = view.findViewById(R.id.tvCurrentAction)
        switchCameraService = view.findViewById(R.id.switchCameraService)

        val btnSwitchCamera = view.findViewById<ImageButton>(R.id.btnSwitchCamera)

        btnSwitchCamera.setOnClickListener {
            val options = arrayOf("Cámara Frontal", "Cámara Trasera", "Cámara Wi-Fi IP")
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Seleccionar Cámara")
                .setSingleChoiceItems(options, cameraMode) { dialog, which ->
                    cameraMode = which
                    if (cameraMode == 2) {
                        showIpCameraDialog()
                    } else {
                        if (switchCameraService.isChecked) {
                            startCameraService()
                        }
                    }
                    dialog.dismiss()
                }
                .show()
        }

        switchCameraService.isChecked = CameraSharedState.isServiceRunning
        switchCameraService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (cameraMode == 2 && ipCameraUrl.isEmpty()) {
                    showIpCameraDialog()
                } else {
                    startCameraService()
                }
            } else {
                stopCameraService()
            }
        }
    }

    private fun showIpCameraDialog() {
        val input = android.widget.EditText(requireContext())
        input.hint = "http://192.168.1.100:81/stream"
        input.setText(ipCameraUrl)
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("URL de Cámara Wi-Fi")
            .setView(input)
            .setPositiveButton("Conectar") { _, _ ->
                ipCameraUrl = input.text.toString()
                switchCameraService.isChecked = true
                startCameraService()
            }
            .setNegativeButton("Cancelar") { _, _ ->
                if (!CameraSharedState.isServiceRunning) {
                    switchCameraService.isChecked = false
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
        if (::switchCameraService.isInitialized) {
            switchCameraService.setOnCheckedChangeListener(null)
            switchCameraService.isChecked = CameraSharedState.isServiceRunning
            switchCameraService.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    if (cameraMode == 2 && ipCameraUrl.isEmpty()) {
                        showIpCameraDialog()
                    } else {
                        startCameraService()
                    }
                } else {
                    stopCameraService()
                }
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

    private fun setupRecyclerView(view: View) {
        val rvGestos = view.findViewById<RecyclerView>(R.id.rvGestos)
        rvGestos.layoutManager = GridLayoutManager(requireContext(), 2)
        gestureAdapter = GestureAdapter(
            getDeviceName = { deviceId ->
                if (deviceId == null) "Sin asignar"
                else dispositivosLocales.find { it.id == deviceId }?.nombre ?: "Desconocido"
            },
            onEditClick = { showGestureDialog(it) },
            onDeleteClick = { deleteGesto(it) },
            onToggleClick = { gesto, isChecked ->
                Toast.makeText(requireContext(), "${gesto.nombre} -> $isChecked", Toast.LENGTH_SHORT).show()
            }
        )
        rvGestos.adapter = gestureAdapter
    }

    private fun syncGestosFromApi() {
        viewLifecycleOwner.lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = requireActivity(),
                showLoading = false,
                apiCall = {
                    val token = requireContext().getSharedPreferences("SesionApp", Context.MODE_PRIVATE).getString("apiToken", "") ?: ""
                    RetrofitClient.gestureService.getGestos("Bearer $token")
                },
                onSuccess = { response ->
                    val apiGestos = response.data
                    withContext(Dispatchers.IO) {
                        db.gestoDao().deleteAllGestos()
                        db.gestoDao().insertAll(apiGestos)
                    }
                },
                onError = {
                    Log.e("GestosFragment", "Error syncing gestos: $it")
                }
            )
        }
    }

    private fun showGestureDialog(gestoExistente: Gesto?) {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setContentView(R.layout.dialog_gesture_form)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val etName = dialog.findViewById<MaterialAutoCompleteTextView>(R.id.etGestureName)
        val spinnerDevices = dialog.findViewById<Spinner>(R.id.spinnerDevices)

        val deviceNames = dispositivosLocales.map { it.nombre ?: "Desconocido" }.toMutableList()
        deviceNames.add(0, "Ninguno (Sin asignar)")
        
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, deviceNames)
        spinnerDevices.adapter = adapter

        val gestosValidos = listOf("Manos Arriba", "Una Mano Arriba", "Agitar la Mano", "Abrir Puño", "Cerrar Puño")
        val gestoAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, gestosValidos)
        etName.setAdapter(gestoAdapter)

        if (gestoExistente != null) {
            etName.setText(gestoExistente.nombre, false)
            
            val deviceIndex = dispositivosLocales.indexOfFirst { it.id == gestoExistente.aparatoId }
            if (deviceIndex != -1) {
                spinnerDevices.setSelection(deviceIndex + 1)
            }
        }

        dialog.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.findViewById<Button>(R.id.btnSave).setOnClickListener {
            val name = etName.text.toString()

            if (name.isNotBlank()) {
                val selectedPos = spinnerDevices.selectedItemPosition
                val assignedDeviceId = if (selectedPos > 0) dispositivosLocales[selectedPos - 1].id else null

                val confianzaAutomatica = when (name) {
                    "Manos Arriba" -> 85.0
                    "Cerrar Puño" -> 85.0
                    "Una Mano Arriba" -> 80.0
                    "Abrir Puño" -> 80.0
                    "Agitar la Mano" -> 70.0
                    else -> 80.0
                }

                val nuevoGesto = Gesto(
                    id = gestoExistente?.id ?: 0,
                    bkId = gestoExistente?.bkId ?: 0,
                    nombre = name,
                    identificadorIa = gestoExistente?.identificadorIa ?: 1,
                    nivelConfianzaMinimo = confianzaAutomatica,
                    tipoDisparadorNombre = "Continuo",
                    aparatoId = assignedDeviceId
                )
                saveGesto(nuevoGesto, isUpdate = gestoExistente != null)
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "Debe seleccionar un gesto", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun saveGesto(gesto: Gesto, isUpdate: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = requireActivity(),
                showLoading = true,
                loadingTitle = "Guardando",
                loadingMessage = "Guardando gesto...",
                apiCall = {
                    val token = requireContext().getSharedPreferences("SesionApp", Context.MODE_PRIVATE).getString("apiToken", "") ?: ""
                    val bearer = "Bearer $token"
                    if (isUpdate) {
                        RetrofitClient.gestureService.updateGesto(bearer, gesto.id, gesto)
                        retrofit2.Response.success(com.example.android.network.ApiResponse(true, 200, gesto))
                    } else {
                        RetrofitClient.gestureService.createGesto(bearer, gesto)
                    }
                },
                onSuccess = { response ->
                    val savedGesto = response.data
                    if (savedGesto != null) {
                        withContext(Dispatchers.IO) {
                            db.gestoDao().insertGesto(savedGesto)
                        }
                        Toast.makeText(requireContext(), "Guardado exitoso", Toast.LENGTH_SHORT).show()
                    }
                },
                onError = { errorMsg ->
                    if (errorMsg != "Sesión expirada") {
                        Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    private fun deleteGesto(gesto: Gesto) {
        viewLifecycleOwner.lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = requireActivity(),
                showLoading = true,
                loadingTitle = "Eliminando",
                loadingMessage = "Eliminando gesto...",
                apiCall = {
                    val token = requireContext().getSharedPreferences("SesionApp", Context.MODE_PRIVATE).getString("apiToken", "") ?: ""
                    RetrofitClient.gestureService.deleteGesto("Bearer $token", gesto.id)
                },
                onSuccess = {
                    withContext(Dispatchers.IO) {
                        db.gestoDao().deleteGesto(gesto)
                    }
                    Toast.makeText(requireContext(), "Eliminado exitoso", Toast.LENGTH_SHORT).show()
                },
                onError = { errorMsg ->
                    if (errorMsg != "Sesión expirada") {
                        Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}
