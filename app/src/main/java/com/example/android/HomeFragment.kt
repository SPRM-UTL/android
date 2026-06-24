package com.example.android

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
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.android.db.AppDatabase
import com.example.android.network.ApiHandler
import com.example.android.network.BluetoothController
import com.example.android.network.RetrofitClient
import com.example.android.ui.AddDeviceAdapter
import com.example.android.ui.DeviceAdapter
import com.example.android.view.Snackbars
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence

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

    private lateinit var btnConfigurarRed: View

    private lateinit var deviceAdapter: DeviceAdapter

    private var isLoggingOut = false
    private var pollingJob: kotlinx.coroutines.Job? = null
    
    private var dispositivosJob: kotlinx.coroutines.Job? = null
    private var currentCasaId: Int? = null

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
    }

    override fun onPause() {
        super.onPause()
        pollingJob?.cancel()
    }

    private fun iniciarPollingEstadoRed() {
        pollingJob?.cancel()
        pollingJob = viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                verificarEstadoRedVisual()
                delay(3000)
            }
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
    }

    private fun inicializarAdapters() {
        deviceAdapter = DeviceAdapter(
            onEditClick = { dispositivo ->
                mostrarInformacionDispositivo(dispositivo)
            },
            onDeleteClick = { dispositivo ->
                mostrarDialogoEliminar(dispositivo)
            },
            onToggleClick = { dispositivo, isChecked ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val response = RetrofitClient.deviceService.toggleAparato(dispositivo.id, isChecked)
                        if (response.isSuccessful) {
                            Snackbars.success(
                                mainHome,
                                "${dispositivo.nombre} ${if (isChecked) "Encendido" else "Apagado"}",
                                Snackbar.LENGTH_SHORT
                            ).show()
                        } else {
                            Snackbars.error(
                                mainHome,
                                "Error al conectar con ${dispositivo.nombre}. Verifica que esté encendido.",
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                    } catch (e: Exception) {
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

    private fun mostrarDialogoEliminar(dispositivo: com.example.android.db.Dispositivo) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar dispositivo")
            .setMessage("¿Estás seguro de que deseas eliminar '${dispositivo.nombre}' de tu cuenta? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { dialog, _ ->
                eliminarDispositivoDeApi(dispositivo)
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun eliminarDispositivoDeApi(dispositivo: com.example.android.db.Dispositivo) {
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
                    Snackbars.success(mainHome, "Dispositivo eliminado con éxito", Snackbar.LENGTH_SHORT).show()
                    cargarDatos()
                },
                onError = { error ->
                    Snackbars.error(mainHome, "Error al eliminar: $error", Snackbar.LENGTH_LONG).show()
                }
            )
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
        
        configurarAnimacionInicial()
        mostrarMensajeBienvenida()
        mostrarTutorial()
    }

    private fun mostrarTutorial() {
        val sharedPref = requireContext().getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val tutorialVisto = sharedPref.getBoolean("tutorial_visto", false)

        if (tutorialVisto) return

        vistaRaiz.postDelayed({
            if (!isAdded) return@postDelayed

            val typeFace = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

            val targetRed = TapTarget.forView(
                btnConfigurarRed,
                "1. Conecta tu Hogar",
                "Lo primero es configurar y vincular tu hardware controlador (ESP32) para que la app pueda comunicarse con tu casa."
            )
                .outerCircleColor(R.color.teal_primary)
                .outerCircleAlpha(0.96f)
                .targetCircleColor(R.color.white)
                .titleTextSize(22)
                .titleTextColor(R.color.white)
                .descriptionTextSize(14)
                .descriptionTextColor(R.color.white)
                .textColor(R.color.white)
                .textTypeface(typeFace)
                .dimColor(R.color.black)
                .drawShadow(true)
                .cancelable(false)
                .tintTarget(true)
                .transparentTarget(true)
                .targetRadius(30)

            val targetDispositivos = TapTarget.forView(
                vistaRaiz.findViewById(R.id.tvTituloDispositivos) ?: rvDispositivos,
                "2. Añade y Controla",
                "Una vez conectado, agrega tus dispositivos (focos, ventiladores, etc.) aquí para controlarlos manualmente con un solo toque."
            )
                .outerCircleColor(R.color.teal_primary)
                .outerCircleAlpha(0.96f)
                .targetCircleColor(R.color.white)
                .titleTextSize(22)
                .titleTextColor(R.color.white)
                .descriptionTextSize(14)
                .descriptionTextColor(R.color.white)
                .textColor(R.color.white)
                .textTypeface(typeFace)
                .dimColor(R.color.black)
                .drawShadow(true)
                .cancelable(false)
                .tintTarget(true)
                .transparentTarget(true)
                .targetRadius(60)

            val targetIA = TapTarget.forView(
                requireActivity().findViewById(R.id.bottom_bar_container),
                "3. Inteligencia Artificial",
                "Abre el menú central para activar la cámara, crear secuencias de gestos corporales y programar rutinas automatizadas."
            )
                .outerCircleColor(R.color.teal_primary)
                .outerCircleAlpha(0.96f)
                .targetCircleColor(R.color.white)
                .titleTextSize(22)
                .titleTextColor(R.color.white)
                .descriptionTextSize(14)
                .descriptionTextColor(R.color.white)
                .textColor(R.color.white)
                .textTypeface(typeFace)
                .dimColor(R.color.black)
                .drawShadow(true)
                .cancelable(false)
                .tintTarget(true)
                .transparentTarget(true)
                .targetRadius(70)

            val targetPerfil = TapTarget.forView(
                vistaRaiz.findViewById(R.id.profileCircle),
                "4. Tu Perfil",
                "Accede aquí para ajustar tus preferencias, habilitar la huella dactilar y cerrar tu sesión."
            )
                .outerCircleColor(R.color.teal_primary)
                .outerCircleAlpha(0.96f)
                .targetCircleColor(R.color.white)
                .titleTextSize(22)
                .titleTextColor(R.color.white)
                .descriptionTextSize(14)
                .descriptionTextColor(R.color.white)
                .textColor(R.color.white)
                .textTypeface(typeFace)
                .dimColor(R.color.black)
                .drawShadow(true)
                .cancelable(false)
                .tintTarget(false)
                .transparentTarget(true)
                .targetRadius(40)

            TapTargetSequence(requireActivity())
                .targets(targetRed, targetDispositivos, targetIA, targetPerfil)
                .listener(object : TapTargetSequence.Listener {
                    override fun onSequenceFinish() {
                        sharedPref.edit().putBoolean("tutorial_visto", true).apply()
                    }

                    override fun onSequenceStep(lastTarget: TapTarget?, targetClicked: Boolean) {}

                    override fun onSequenceCanceled(lastTarget: TapTarget?) {
                        sharedPref.edit().putBoolean("tutorial_visto", true).apply()
                    }
                })
                .start()
        }, 800)
    }

    private fun configurarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(mainHome) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, 0)

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

                val snackbar = Snackbars.info(mainHome, "Bienvenido", Snackbar.LENGTH_SHORT)

                val bottomBar = activity.findViewById<View>(R.id.bottom_bar_container)
                val bottomBarHeight = bottomBar?.height ?: 0

                val params = snackbar.view.layoutParams as ViewGroup.MarginLayoutParams
                params.bottomMargin = bottomBarHeight
                snackbar.view.layoutParams = params

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

        vistaRaiz.findViewById<ImageButton>(R.id.btnAddCasa)?.setOnClickListener {
            mostrarDialogoAgregarCasa()
        }

        vistaRaiz.findViewById<ImageButton>(R.id.btnAddHabitacion)?.setOnClickListener {
            if (currentCasaId != null) {
                mostrarDialogoAgregarHabitacion(currentCasaId!!)
            } else {
                Toast.makeText(requireContext(), "Selecciona una casa primero", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun mostrarDialogoAgregarCasa() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_casa, null)
        val dialog = MaterialAlertDialogBuilder(requireContext(), com.google.android.material.R.style.Theme_Material3_Light_Dialog)
            .setView(dialogView)
            .show()

        val etNombreCasa = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNombreCasa)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnConfirm = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirm)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            val nombreCasa = etNombreCasa.text?.toString()?.trim() ?: ""
            if (nombreCasa.isNotEmpty()) {
                crearNuevaCasa(nombreCasa)
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "Ingresa un nombre válido", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun crearNuevaCasa(nombre: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val sharedPref = requireContext().getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
            val token = sharedPref.getString("apiToken", "") ?: ""
            val userId = sharedPref.getInt("userId", 0)
            
            val nuevaCasa = com.example.android.db.Casa(id = 0, nombre = nombre, skUsuarioId = userId)
            
            ApiHandler.safeApiCall(
                activity = requireActivity(),
                showLoading = true,
                loadingTitle = "Creando...",
                apiCall = { RetrofitClient.casaService.createCasa("Bearer $token", nuevaCasa) },
                onSuccess = { response ->
                    sincronizarDatosServidor()
                }
            )
        }
    }

    private fun mostrarDialogoAgregarHabitacion(casaId: Int) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_habitacion, null)
        val dialog = MaterialAlertDialogBuilder(requireContext(), com.google.android.material.R.style.Theme_Material3_Light_Dialog)
            .setView(dialogView)
            .show()

        val etNombreHabitacion = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNombreHabitacion)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnConfirm = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirm)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            val nombreHabitacion = etNombreHabitacion.text?.toString()?.trim() ?: ""
            if (nombreHabitacion.isNotEmpty()) {
                crearNuevaHabitacion(nombreHabitacion, casaId)
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "Ingresa un nombre válido", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun crearNuevaHabitacion(nombre: String, casaId: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            val sharedPref = requireContext().getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
            val token = sharedPref.getString("apiToken", "") ?: ""
            
            val nuevaHab = com.example.android.db.Habitacion(id = 0, nombre = nombre, skCasaId = casaId)
            
            ApiHandler.safeApiCall(
                activity = requireActivity(),
                showLoading = true,
                loadingTitle = "Creando...",
                apiCall = { RetrofitClient.habitacionService.createHabitacion("Bearer $token", nuevaHab) },
                onSuccess = { response ->
                    sincronizarDatosServidor()
                }
            )
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
        rvDispositivos.adapter = ConcatAdapter(deviceAdapter, addDeviceAdapter)

        cargarTabsCasas()
    }

    private fun cargarTabsCasas() {
        viewLifecycleOwner.lifecycleScope.launch {
            db.casaDao().getAllCasas().collectLatest { casas ->
                val tabLayout = vistaRaiz.findViewById<TabLayout>(R.id.tabLayoutCasas)
                tabLayout.removeAllTabs()
                
                if (casas.isEmpty()) {
                    tabLayout.visibility = View.GONE
                    currentCasaId = null
                    observarDispositivos()
                    return@collectLatest
                }
                
                tabLayout.visibility = View.VISIBLE
                
                casas.forEach { casa ->
                    val tab = tabLayout.newTab().setText(casa.nombre)
                    tab.tag = casa.id
                    tabLayout.addTab(tab)
                    if (currentCasaId == casa.id) {
                        tabLayout.selectTab(tab)
                    }
                }
                
                tabLayout.clearOnTabSelectedListeners()
                tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(tab: TabLayout.Tab?) {
                        currentCasaId = tab?.tag as? Int
                        observarDispositivos()
                    }
                    override fun onTabUnselected(tab: TabLayout.Tab?) {}
                    override fun onTabReselected(tab: TabLayout.Tab?) {}
                })
                
                if (currentCasaId == null && casas.isNotEmpty()) {
                    currentCasaId = casas[0].id
                    tabLayout.getTabAt(0)?.select()
                    observarDispositivos()
                }
            }
        }
    }

    private fun observarDispositivos() {
        dispositivosJob?.cancel()
        dispositivosJob = viewLifecycleOwner.lifecycleScope.launch {
            val flow = if (currentCasaId != null) {
                db.dispositivoDao().getDispositivosByCasaId(currentCasaId!!)
            } else {
                db.dispositivoDao().getAllDispositivos()
            }
            flow.collectLatest { dispositivos ->
                if (!isLoggingOut) {
                    deviceAdapter.submitList(dispositivos)
                    actualizarContadorDispositivos(dispositivos.size)
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
                        onSuccess = { casas ->
                            withContext(Dispatchers.IO) {
                                db.casaDao().deleteAllCasas()
                                db.casaDao().insertAll(casas)
                            }
                            
                            if (casas.isEmpty()) {
                                if (isAdded) {
                                    mostrarDialogoAgregarCasa()
                                }
                            } else {
                                // Sincronizar Habitaciones para cada casa
                                val sharedPref = requireContext().getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
                                val token = sharedPref.getString("apiToken", "") ?: ""
                                
                                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                                    db.habitacionDao().deleteAllHabitaciones()
                                    var hasHabitaciones = false
                                    casas.forEach { casa ->
                                        try {
                                            val habResponse = RetrofitClient.habitacionService.getHabitacionesByCasa("Bearer $token", casa.id)
                                            if (habResponse.isSuccessful) {
                                                habResponse.body()?.let { habitaciones ->
                                                    db.habitacionDao().insertAll(habitaciones)
                                                    if (habitaciones.isNotEmpty()) {
                                                        hasHabitaciones = true
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {}
                                    }
                                    
                                    if (!hasHabitaciones && isAdded) {
                                        withContext(Dispatchers.Main) {
                                            mostrarDialogoAgregarHabitacion(casas[0].id)
                                        }
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
                startActivity(Intent(requireContext(), EspConfigActivity::class.java))
                delay(1000)
            } finally {
                btnConfigurarRed.isEnabled = true
            }
        }
    }

    private suspend fun verificarEstadoRedVisual() {
        val sharedPref = requireContext().getSharedPreferences("EspConfigPrefs", Context.MODE_PRIVATE)
        val savedMac = sharedPref.getString("saved_mac_address", "") ?: ""

        if (savedMac.isBlank()) {
            actualizarUiEstadoRed(false)
            return
        }

        try {
            val response = RetrofitClient.deviceService.getWsStatus(savedMac)
            if (response.isSuccessful) {
                actualizarUiEstadoRed(response.body()?.connected == true)
            } else {
                actualizarUiEstadoRed(false)
            }
        } catch (e: Exception) {
            actualizarUiEstadoRed(false)
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

    private fun mostrarInformacionDispositivo(dispositivo: com.example.android.db.Dispositivo) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_device_info, null)
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        dialog.setContentView(dialogView)

        dialogView.findViewById<TextView>(R.id.tvDialogNombre).text = dispositivo.nombre ?: "Desconocido"
        dialogView.findViewById<TextView>(R.id.tvDialogTipo).text = dispositivo.tipo ?: "Desconocido"
        dialogView.findViewById<TextView>(R.id.tvDialogAccion).text = dispositivo.accion ?: "N/A"
        dialogView.findViewById<TextView>(R.id.tvDialogMac).text = dispositivo.macBluetooth ?: "N/A"

        dialogView.findViewById<ImageView>(R.id.btnDialogClose).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDialogEditar).setOnClickListener {
            dialog.dismiss()
            requireActivity().supportFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                .add(R.id.fragment_container_overlay, EditDeviceFragment.newInstance(dispositivo.id))
                .addToBackStack("EditDevice")
                .commit()
        }

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDialogControles).setOnClickListener {
            dialog.dismiss()
            val intent = Intent(requireContext(), DeviceControlsActivity::class.java).apply {
                putExtra("EXTRA_DEVICE_ID", dispositivo.id)
            }
            startActivity(intent)
        }

        dialog.show()
    }
}