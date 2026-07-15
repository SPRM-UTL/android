package com.example.android.feature.home
import com.example.android.core.ui.adapters.DeviceAdapter
import com.example.android.feature.home.LugaresActivity
import com.example.android.feature.home.HomeTutorialHelper
import com.example.android.core.ui.adapters.AddDeviceAdapter
import com.example.android.feature.ai.SecuenciaConfigManager
import com.example.android.feature.ai.AIVisionActivity
import com.example.android.core.db.models.Dispositivo

import com.example.android.R

import android.graphics.Color
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import coil.load
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.android.core.db.init.AppDatabase
import com.example.android.core.network.api.ApiHandler
import com.example.android.core.network.bluetooth.BluetoothController
import com.example.android.core.network.client.RetrofitClient
import com.example.android.core.view.Snackbars
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import com.example.android.core.ui.dialogs.DeviceInfoDialog
import com.example.android.core.ui.dialogs.DeleteDeviceDialog
import kotlinx.coroutines.withContext
import com.example.android.feature.device.SelectTypeDevice
import com.example.android.feature.profile.ProfileActivity
import com.example.android.feature.network_config.EspConfigActivity

class HomeFragment : Fragment() {

    private lateinit var db: AppDatabase

    private lateinit var vistaRaiz: View
    private lateinit var mainHome: MotionLayout

    private lateinit var rvDispositivos: RecyclerView

    private lateinit var tvDeviceCount: TextView
    private lateinit var tvRedEstado: TextView
    private lateinit var iconWifiContainer: MaterialCardView
    private lateinit var iconWifi: ImageView
    private lateinit var tvUsuario: TextView
    private lateinit var ivProfile: ImageView

    private lateinit var btnConfigurarRed: View

    private lateinit var deviceAdapter: DeviceAdapter
    private lateinit var deviceInfoDialog: DeviceInfoDialog
    private lateinit var deleteDeviceDialog: DeleteDeviceDialog

    private var isLoggingOut = false
    private var pollingJob: kotlinx.coroutines.Job? = null
    private var pollCount = 0
    
    private var dispositivosJob: kotlinx.coroutines.Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getDatabase(requireContext())
        inicializarVistas(view)
        inicializarAdapters()

        configurarUI()
        configurarEventos()
        cargarDatos()
    }

    override fun onResume() {
        super.onResume()
        iniciarPollingEstadoRed()
        cargarFotoPerfil()
    }

    private fun cargarFotoPerfil() {
        val sharedPreferences = requireContext().getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val profileImageUrl = sharedPreferences.getString("profileImageUrl", null)

        if (!profileImageUrl.isNullOrBlank()) {
            ivProfile.load(profileImageUrl) {
                placeholder(R.drawable.ic_manordomo_sin_fondo)
                error(R.drawable.ic_manordomo_sin_fondo)
                crossfade(true)
            }
        } else {
            ivProfile.setImageResource(R.drawable.ic_manordomo_sin_fondo)
        }
    }

    override fun onPause() {
        super.onPause()
        pollingJob?.cancel()
    }

    private fun iniciarPollingEstadoRed() {
        pollingJob?.cancel()
        pollingJob = viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                verificarEstadoDispositivos()
                delay(3000)
            }
        }
    }

    private suspend fun verificarEstadoDispositivos() {
        try {
            val response = RetrofitClient.deviceService.getWsStatusAll()
            if (response.isSuccessful) {
                val macs = response.body()?.connectedDevices ?: emptyList()
                val macsSet = macs.toSet()
                deviceAdapter.actualizarEstados(macsSet)
                actualizarDialogoInformacion(macsSet)
                
                // Actualizar UI del Estado Red (Cámara)
                val sharedPref = requireContext().getSharedPreferences("EspConfigPrefs", Context.MODE_PRIVATE)
                val savedMac = sharedPref.getString("saved_mac_address", "") ?: ""
                if (savedMac.isNotBlank()) {
                    actualizarUiEstadoRed(macsSet.any { it.equals(savedMac, ignoreCase = true) })
                } else {
                    actualizarUiEstadoRed(false)
                }

                pollCount++
                if (pollCount % 5 == 0) {
                    refrescarEstadosDesdeServidor()
                }
            } else {
                deviceAdapter.actualizarEstados(emptySet())
                actualizarDialogoInformacion(emptySet())
                actualizarUiEstadoRed(false)
            }
        } catch (e: Exception) {
            deviceAdapter.actualizarEstados(emptySet())
            actualizarDialogoInformacion(emptySet())
            actualizarUiEstadoRed(false)
        }
    }

    private suspend fun refrescarEstadosDesdeServidor() {
        try {
            val sharedPref = requireContext().getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
            val token = sharedPref.getString("apiToken", "") ?: return
            val response = RetrofitClient.deviceService.getDispositivos("Bearer $token")
            if (response.isSuccessful) {
                val dispositivos = response.body()?.data ?: return
                withContext(Dispatchers.IO) {
                    dispositivos.forEach { db.dispositivoDao().insertDispositivo(it) }
                }
                deviceAdapter.sincronizarEstadosDesdeDispositivos(dispositivos)
            }
        } catch (_: Exception) {
        }
    }

    private fun actualizarDialogoInformacion(connectedMacs: Set<String>) {
        if (::deviceInfoDialog.isInitialized) {
            deviceInfoDialog.updateConnectionStatus(connectedMacs)
        }
    }

    private fun inicializarVistas(view: View) {
        vistaRaiz          = view
        mainHome           = view.findViewById(R.id.mainHome)
        rvDispositivos     = view.findViewById(R.id.rvDispositivosHome)
        tvDeviceCount      = view.findViewById(R.id.tvDeviceCount)
        tvRedEstado        = view.findViewById(R.id.tvRedEstado)
        iconWifiContainer  = view.findViewById(R.id.iconWifiContainer)
        iconWifi           = view.findViewById(R.id.iconWifi)
        btnConfigurarRed   = view.findViewById(R.id.btnConfigurarRed)
        tvUsuario          = view.findViewById(R.id.tvUsuario)
        ivProfile          = view.findViewById(R.id.ivProfile)
        
        val redContainer = view.findViewById<View>(R.id.tvRedEstado)?.parent as? View
        redContainer?.setOnClickListener {
            val sharedPref = requireContext().getSharedPreferences("EspConfigPrefs", Context.MODE_PRIVATE)
            val savedMac = sharedPref.getString("saved_mac_address", "") ?: ""
            if (savedMac.isNotBlank()) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val dispositivo = withContext(Dispatchers.IO) {
                        db.dispositivoDao().getAllDispositivosOnce().find { it.macBluetooth.equals(savedMac, ignoreCase = true) }
                    }
                    if (dispositivo != null) {
                        val intent = Intent(requireContext(), com.example.android.feature.ai.AIVisionActivity::class.java).apply {
                            putExtra("dispositivo_id", dispositivo.id)
                        }
                        startActivity(intent)
                    } else {
                        Snackbars.info(mainHome, "La cámara aún no se ha sincronizado", Snackbar.LENGTH_SHORT).show()
                    }
                }
            } else {
                Snackbars.info(mainHome, "Configura la red primero", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun inicializarAdapters() {
        deviceInfoDialog = DeviceInfoDialog(
            fragment = this,
            isDeviceConnected = { mac ->
                mac != null && ::deviceAdapter.isInitialized && deviceAdapter.isDeviceConnected(mac)
            }
        )

        deleteDeviceDialog = DeleteDeviceDialog(
            fragment = this,
            onConfirm = { dispositivo ->
                eliminarDispositivoDeApi(dispositivo)
            }
        )

        deviceAdapter = DeviceAdapter(
            onEditClick = { dispositivo ->
                deviceInfoDialog.show(dispositivo)
            },
            onDeleteClick = { dispositivo ->
                deleteDeviceDialog.show(dispositivo)
            },
            onToggleClick = { dispositivo, isChecked ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val response = RetrofitClient.deviceService.toggleAparato(dispositivo.id, isChecked)
                        if (response.isSuccessful) {
                            val estadoConfirmado = response.body()?.estadoEncendido ?: isChecked
                            deviceAdapter.actualizarEstadoDispositivo(dispositivo.id, estadoConfirmado)
                            Snackbars.success(
                                mainHome,
                                "${dispositivo.nombre} ${if (isChecked) "encendido" else "apagado"}",
                                Snackbar.LENGTH_SHORT
                            ).show()
                        } else {
                            deviceAdapter.actualizarEstadoDispositivo(dispositivo.id, !isChecked)
                            Snackbars.error(
                                mainHome,
                                "${dispositivo.nombre}: sin conexión al servidor. ¿Está el ESP32 conectado?",
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                    } catch (e: Exception) {
                        deviceAdapter.actualizarEstadoDispositivo(dispositivo.id, !isChecked)
                        Snackbars.error(
                            mainHome,
                            "Error de red al intentar controlar ${dispositivo.nombre}.",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
    }

    private fun eliminarDispositivoDeApi(dispositivo: com.example.android.core.db.models.Dispositivo) {
        val sharedPreferences = requireContext().getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("apiToken", null) ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = requireActivity(),
                showLoading = true,
                loadingTitle = "Eliminando",
                loadingMessage = "Borrando dispositivo...",
                apiCall = { RetrofitClient.deviceService.deleteDispositivo("Bearer $token", dispositivo.id) },
                onSuccess = {
                    eliminarDispositivoLocal(dispositivo)
                    Snackbars.success(mainHome, "Dispositivo eliminado con éxito", Snackbar.LENGTH_SHORT).show()
                },
                onError = { error ->
                    if (error == ApiHandler.RESOURCE_NOT_FOUND_MESSAGE) {
                        eliminarDispositivoLocal(dispositivo)
                        Snackbars.info(
                            mainHome,
                            "El dispositivo ya no existía en el servidor. Se limpió de tu lista.",
                            Snackbar.LENGTH_LONG
                        ).show()
                    } else {
                        Snackbars.error(mainHome, "Error al eliminar: $error", Snackbar.LENGTH_LONG).show()
                    }
                }
            )
        }
    }

    private suspend fun eliminarDispositivoLocal(dispositivo: com.example.android.core.db.models.Dispositivo) {
        withContext(Dispatchers.IO) {
            db.dispositivoDao().deleteDispositivoById(dispositivo.id)
        }
    }

    private fun configurarUI() {
        configurarInsets()
        cargarIconos()
        
        val sharedPreferences = requireContext().getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val nombreUsuario = sharedPreferences.getString("userName", "Mayordomo")
        if (!nombreUsuario.isNullOrBlank()) {
            tvUsuario.text = nombreUsuario
        }
        
        // La foto de perfil se carga ahora en onResume()
        
        configurarAnimacionInicial()
        mostrarMensajeBienvenida()
        mostrarTutorial()
    }

    private fun mostrarTutorial() {
        HomeTutorialHelper().mostrarTutorial(
            fragment = this,
            vistaRaiz = vistaRaiz,
            btnConfigurarRed = btnConfigurarRed,
            rvDispositivos = rvDispositivos
        )
    }

    private fun configurarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(mainHome) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, 0, bars.right, 0)

            val cardSuperior = view.findViewById<View>(R.id.cardSuperior)
            if (cardSuperior != null) {
                cardSuperior.setPadding(
                    cardSuperior.paddingLeft,
                    bars.top + (4 * resources.displayMetrics.density).toInt(),
                    cardSuperior.paddingRight,
                    cardSuperior.paddingBottom
                )
            }

            val headerBg = view.findViewById<View>(R.id.headerBackground)
            if (headerBg != null && view is androidx.constraintlayout.motion.widget.MotionLayout) {
                val newHeight = bars.top + (165 * resources.displayMetrics.density).toInt()
                
                val startSet = view.getConstraintSet(R.id.start)
                startSet?.constrainHeight(R.id.headerBackground, newHeight)
                if (startSet != null) view.updateState(R.id.start, startSet)
                
                val endSet = view.getConstraintSet(R.id.end)
                endSet?.constrainHeight(R.id.headerBackground, newHeight)
                if (endSet != null) view.updateState(R.id.end, endSet)
                
                // Actualizar de inmediato
                val bgParams = headerBg.layoutParams
                bgParams.height = newHeight
                headerBg.layoutParams = bgParams
            } else if (headerBg != null) {
                val bgParams = headerBg.layoutParams
                bgParams.height = bars.top + (165 * resources.displayMetrics.density).toInt()
                headerBg.layoutParams = bgParams
            }

            val scrollView = view.findViewById<androidx.core.widget.NestedScrollView>(R.id.scrollContainer)
            scrollView?.setPadding(
                scrollView.paddingLeft,
                scrollView.paddingTop,
                scrollView.paddingRight,
                bars.bottom + (90 * resources.displayMetrics.density).toInt()
            )
            insets
        }
    }

    private fun cargarIconos() {
        iconWifi.setImageResource(R.drawable.wifi)
        iconWifi.imageTintList = ColorStateList.valueOf(requireContext().getColor(R.color.white))
    }

    private fun configurarAnimacionInicial() {
        val activityManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (!activityManager.isLowRamDevice) {
            mainHome.post { mainHome.transitionToEnd() }
        } else {
            mainHome.progress = 1f
        }
    }

    // ==========================================================
    // FIX: el Snackbar se muestra anclado a "mainHome" (que ya
    // respeta los insets) en lugar de android.R.id.content, y se
    // le agrega un margen inferior calculado según la altura real
    // de bottom_bar_container, para que no quede pegado al borde
    // ni tapado por la barra de navegación inferior.
    // ==========================================================
    private fun mostrarMensajeBienvenida() {
        if (!isAdded) return

        val activity = activity ?: return
        if (activity.intent.getBooleanExtra("SHOW_WELCOME", false)) {

            activity.intent.removeExtra("SHOW_WELCOME")

            vistaRaiz.post {
                if (!isAdded) return@post

                // 1. Creamos el snackbar normal de Material Design
                val snackbar = Snackbars.info(mainHome, "Bienvenido", Snackbar.LENGTH_SHORT)

                // 2. Buscamos el contenedor de la barra inferior de la Activity
                val bottomBar = activity.findViewById<View>(R.id.bottom_bar_container)

                if (bottomBar != null) {
                    // 3. Lo anclamos directamente sobre el contenedor.
                    // Esto lo posiciona automáticamente arriba de la barra y del botón '+' sin romper el diseño.
                    snackbar.setAnchorView(bottomBar)
                } else {
                    // Respaldo manual usando padding en caso de que no se encuentre la vista en el layout actual
                    val bottomBarHeight = bottomBar?.height ?: 0
                    val params = snackbar.view.layoutParams as ViewGroup.MarginLayoutParams
                    params.bottomMargin = bottomBarHeight + (16 * resources.displayMetrics.density).toInt()
                    snackbar.view.layoutParams = params
                }

                snackbar.show()
            }
        }
    }

    private fun configurarEventos() {
        btnConfigurarRed.setOnClickListener {
            abrirConfiguracionRed()
        }
        
        vistaRaiz.findViewById<View>(R.id.profileCircle)?.setOnClickListener {
            val intent = Intent(requireContext(), ProfileActivity::class.java)
            startActivity(intent)
        }

        vistaRaiz.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAdministrarLugares)?.setOnClickListener {
            val intent = Intent(requireContext(), com.example.android.feature.home.LugaresActivity::class.java)
            startActivity(intent)
        }
    }

    private fun cargarDatos() {
        cargarRecycler()
        sincronizarDatosServidor()
        cargarDatosUsuario()
    }

    private fun cargarRecycler() {
        val addDeviceAdapter = AddDeviceAdapter {
            val intent = Intent(requireContext(), SelectTypeDevice::class.java)
            startActivity(intent)
        }

        rvDispositivos.layoutManager = GridLayoutManager(requireContext(), 2)
        rvDispositivos.adapter = ConcatAdapter(addDeviceAdapter, deviceAdapter)

        observarDispositivos()
    }

    private fun observarDispositivos() {
        dispositivosJob?.cancel()
        dispositivosJob = viewLifecycleOwner.lifecycleScope.launch {
            val flow = db.dispositivoDao().getAllDispositivos()
            flow.collectLatest { dispositivos ->
                if (!isLoggingOut) {
                    // Filtrar cámaras para que no aparezcan en la lista "Mis dispositivos"
                    val dispositivosFiltrados = dispositivos.filter { 
                        !it.tipo.equals("Cámara", ignoreCase = true) && 
                        !it.tipo.equals("Cámara ESP32", ignoreCase = true) && 
                        !it.tipo.equals("ESP32-CAM", ignoreCase = true) 
                    }
                    deviceAdapter.sincronizarEstadosDesdeDispositivos(dispositivosFiltrados)
                    deviceAdapter.submitList(dispositivosFiltrados)
                    actualizarContadorDispositivos(dispositivosFiltrados.size)
                }
                actualizarNombreUsuario()
            }
        }
    }

    private fun actualizarNombreUsuario() {
        val sharedPref = requireContext().getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        tvUsuario.text = sharedPref.getString("userName", "Mayordomo")
    }

    private fun cargarDatosUsuario() {
        val sharedPref = requireContext().getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val token = sharedPref.getString("apiToken", "") ?: ""
        val userId = sharedPref.getInt("userId", -1)

        if (userId != -1 && token.isNotEmpty()) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val response = RetrofitClient.apiService.getUsuario("Bearer $token", userId)
                    if (response.isSuccessful && response.body()?.data != null) {
                        val nombre = response.body()?.data?.nombre ?: ""
                        val primerNombre = nombre.split(" ").firstOrNull() ?: "Mayordomo"
                        tvUsuario.text = "Hola, $primerNombre"
                        // Guardar para que esté cacheado en próximos inicios
                        sharedPref.edit().putString("userName", primerNombre).apply()
                    }
                } catch (e: Exception) {
                    // Ignorar error de red silenciosamente en el Home
                }
            }
        }
    }

    private fun actualizarContadorDispositivos(totalDispositivos: Int) {
        tvDeviceCount.text = "$totalDispositivos ${
            if (totalDispositivos == 1) "dispositivo" else "dispositivos"
        } en funcionamiento"
    }

    private fun sincronizarDatosServidor() {
        viewLifecycleOwner.lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = requireActivity(),
                showLoading = false,
                apiCall = {
                    val sharedPref = requireContext().getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
                    val token = sharedPref.getString("apiToken", "") ?: ""
                    RetrofitClient.deviceService.getDispositivos("Bearer $token")
                },
                onSuccess = { dispositivosResponse ->
                    val dispositivos = dispositivosResponse.data
                    deviceAdapter.sincronizarEstadosDesdeDispositivos(dispositivos)
                    withContext(Dispatchers.IO) {
                        db.dispositivoDao().deleteAllDispositivos()
                        db.dispositivoDao().insertAll(dispositivos)
                    }

                    // Sincronizar Casas
                    ApiHandler.safeApiCall(
                        activity = requireActivity(),
                        showLoading = false,
                        apiCall = {
                            val sharedPref = requireContext().getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
                            val token = sharedPref.getString("apiToken", "") ?: ""
                            RetrofitClient.casaService.getCasas("Bearer $token")
                        },
                        onSuccess = { response ->
                            val casas = response.data
                            withContext(Dispatchers.IO) {
                                db.casaDao().deleteAllCasas()
                                if (casas != null) {
                                    db.casaDao().insertAll(casas)
                                    db.habitacionDao().deleteAllHabitaciones()
                                }
                            }
                            
                            if (!casas.isNullOrEmpty()) {
                                // Sincronizar Habitaciones para cada casa
                                val sharedPref = requireContext().getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
                                val token = sharedPref.getString("apiToken", "") ?: ""
                                
                                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                                    casas.forEach { casa ->
                                        try {
                                            val habResponse = RetrofitClient.habitacionService.getHabitacionesByCasa("Bearer $token", casa.id)
                                            if (habResponse.isSuccessful) {
                                                habResponse.body()?.data?.let {
                                                    db.habitacionDao().insertAll(it)
                                                }
                                            }
                                        } catch (e: Exception) {}
                                    }
                                }
                            }
                        }
                    )

                    // Sincronizar Gestos
                    ApiHandler.safeApiCall(
                        activity = requireActivity(),
                        showLoading = false,
                        apiCall = {
                            val sharedPref = requireContext().getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
                            val token = sharedPref.getString("apiToken", "") ?: ""
                            RetrofitClient.gestureService.getGestos("Bearer $token")
                        },
                        onSuccess = { gestosResponse ->
                            val gestos = gestosResponse.data
                            withContext(Dispatchers.IO) {
                                db.gestoDao().deleteAllGestos()
                                db.gestoDao().insertAll(gestos)
                            }
                            
                            // Limpiar los combos locales eliminados en el backend
                            val combosLocales = com.example.android.feature.ai.SecuenciaConfigManager.loadCombos(requireContext()).toMutableList()
                            val gestosIds = gestos.map { it.id }
                            val combosValidos = combosLocales.filter { combo ->
                                combo.backendGestoId == null || gestosIds.contains(combo.backendGestoId)
                            }
                            if (combosLocales.size != combosValidos.size) {
                                com.example.android.feature.ai.SecuenciaConfigManager.saveCombos(requireContext(), combosValidos)
                            }
                        }
                    )
                }
            )
        }
    }

    private fun abrirConfiguracionRed() {
        btnConfigurarRed.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                startActivity(
                    Intent(requireContext(), EspConfigActivity::class.java).apply {
                        putExtra(EspConfigActivity.EXTRA_MODO_SOCKET, true)
                        putExtra(EspConfigActivity.EXTRA_TIPO_DISPOSITIVO, "ESP32-CAM")
                    }
                )
                delay(1000)
            } finally {
                btnConfigurarRed.isEnabled = true
            }
        }
    }

    private fun actualizarUiEstadoRed(isConnected: Boolean) {
        if (isConnected) {
            tvRedEstado.text = "Cámara en línea"
            tvRedEstado.setTextColor(requireContext().getColor(R.color.teal_primary))
            iconWifiContainer.setCardBackgroundColor(requireContext().getColor(R.color.teal_primary))
            iconWifi.imageTintList = ColorStateList.valueOf(requireContext().getColor(R.color.white))
        } else {
            tvRedEstado.text = "Cámara desconectada"
            tvRedEstado.setTextColor(requireContext().getColor(R.color.text_grey))
            iconWifi.imageTintList = ColorStateList.valueOf(requireContext().getColor(R.color.white))
            iconWifiContainer.setCardBackgroundColor(requireContext().getColor(R.color.text_grey))
        }
    }

}
