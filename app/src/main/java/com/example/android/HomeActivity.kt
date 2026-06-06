package com.example.android

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.android.db.AppDatabase
import com.example.android.network.BluetoothController
import com.example.android.network.RetrofitClient
import com.example.android.ui.AddDeviceAdapter
import com.example.android.ui.DeviceAdapter
import com.example.android.ui.components.BottomBarWithFab
import com.example.android.view.Snackbars
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    // ==========================================================
    // VARIABLES
    // ==========================================================

    private lateinit var db: AppDatabase

    private lateinit var vistaRaiz: View
    private lateinit var mainHome: MotionLayout

    private lateinit var rvDispositivos: RecyclerView

    private lateinit var tvRedEstado: TextView
    private lateinit var iconWifiContainer: MaterialCardView
    private lateinit var iconWifi: ImageView

    private lateinit var btnConfigurarRed: View

    private lateinit var deviceAdapter: DeviceAdapter

    private var isLoggingOut = false

    // ==========================================================
    // LIFECYCLE
    // ==========================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = true

        setContentView(R.layout.activity_home)

        inicializar()
        configurarUI()
        configurarEventos()
        cargarDatos()
    }

    override fun onResume() {
        super.onResume()
        verificarEstadoRedVisual()
    }

    // ==========================================================
    // INICIALIZACION
    // ==========================================================

    private fun inicializar() {

        db = AppDatabase.getDatabase(this)

        inicializarVistas()
        inicializarAdapters()
    }

    private fun inicializarVistas() {

        vistaRaiz = findViewById(android.R.id.content)

        mainHome = findViewById(R.id.mainHome)

        rvDispositivos = findViewById(R.id.rvDispositivosHome)

        tvRedEstado = findViewById(R.id.tvRedEstado)

        iconWifiContainer = findViewById(R.id.iconWifiContainer)

        iconWifi = findViewById(R.id.iconWifi)

        btnConfigurarRed = findViewById(R.id.btnConfigurarRed)
    }

    private fun inicializarAdapters() {

        deviceAdapter = DeviceAdapter(

            onEditClick = {
                abrirPantallaDispositivo()
            },

            onDeleteClick = {
            },

            onToggleClick = { dispositivo, isChecked ->

                Snackbars.info(
                    mainHome,
                    "${dispositivo.nombre} ${
                        if (isChecked) "Encendido"
                        else "Apagado"
                    }",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        )
    }

    // ==========================================================
    // UI
    // ==========================================================

    private fun configurarUI() {

        configurarInsets()

        cargarIconos()

        configurarBottomBar()

        configurarAnimacionInicial()

        mostrarMensajeBienvenida()
    }

    private fun configurarInsets() {

        ViewCompat.setOnApplyWindowInsetsListener(mainHome) { view, insets ->

            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                )

            view.setPadding(
                bars.left,
                bars.top,
                bars.right,
                0
            )

            findViewById<View>(R.id.bottom_bar_container)?.setPadding(0, 0, 0, bars.bottom)
            
            // Ajustar el scroll para que el contenido no quede tapado por la barra
            val scrollView = findViewById<androidx.core.widget.NestedScrollView>(R.id.scrollContainer)
            scrollView?.setPadding(scrollView.paddingLeft, scrollView.paddingTop, scrollView.paddingRight, bars.bottom + (16 * resources.displayMetrics.density).toInt())

            insets
        }
    }

    private fun cargarIconos() {

        iconWifi.setImageResource(R.drawable.wifi)
        iconWifi.imageTintList = ColorStateList.valueOf(getColor(R.color.white))

        findViewById<ImageView>(R.id.ivFlechaDerecha)?.let {
            it.setImageResource(R.drawable.arrow_right)
            it.imageTintList = ColorStateList.valueOf(getColor(R.color.teal_primary))
        }
    }

    private fun configurarAnimacionInicial() {

        val activityManager =
            getSystemService(
                Context.ACTIVITY_SERVICE
            ) as ActivityManager

        if (!activityManager.isLowRamDevice) {

            mainHome.post {
                mainHome.transitionToEnd()
            }

        } else {

            mainHome.progress = 1f
        }
    }

    private fun mostrarMensajeBienvenida() {

        if (
            intent.getBooleanExtra(
                "SHOW_WELCOME",
                false
            )
        ) {

            Snackbars.info(
                vistaRaiz,
                "Bienvenido",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun configurarBottomBar() {

        val container =
            findViewById<FrameLayout>(
                R.id.bottom_bar_container
            )

        val composeView = ComposeView(this).apply {

            setContent {

                BottomBarWithFab(

                    onHomeClick = {},

                    onGesturesClick = {

                        startActivity(
                            Intent(
                                this@HomeActivity,
                                Gestos::class.java
                            )
                        )

                        overridePendingTransition(0, 0)
                    },

                    onFabClick = {
                        abrirMenuPrincipal()
                    }
                )
            }
        }

        container.addView(composeView)
    }

    // ==========================================================
    // EVENTOS
    // ==========================================================

    private fun configurarEventos() {

        btnConfigurarRed.setOnClickListener {

            abrirConfiguracionRed()
        }
    }

    // ==========================================================
    // DATOS
    // ==========================================================

    private fun cargarDatos() {

        cargarRecycler()

        sincronizarDatosServidor()
    }

    private fun cargarRecycler() {

        val addDeviceAdapter = AddDeviceAdapter {

            abrirPantallaDispositivo()
        }

        val concatAdapter =
            ConcatAdapter(
                deviceAdapter,
                addDeviceAdapter
            )

        rvDispositivos.layoutManager =
            GridLayoutManager(this, 2)

        rvDispositivos.adapter =
            concatAdapter

        observarDispositivos()
    }

    private fun observarDispositivos() {

        lifecycleScope.launch {

            db.dispositivoDao()
                .getAllDispositivos()
                .collectLatest { dispositivos ->

                    if (!isLoggingOut) {

                        deviceAdapter.submitList(
                            dispositivos
                        )
                    }

                    actualizarNombreUsuario()
                }
        }
    }

    private fun actualizarNombreUsuario() {

        findViewById<TextView>(R.id.tvUsuario)?.let {

            val sharedPref =
                getSharedPreferences(
                    "SesionApp",
                    Context.MODE_PRIVATE
                )

            it.text =
                sharedPref.getString(
                    "userName",
                    "Mayordomo"
                )
        }
    }

    private fun sincronizarDatosServidor() {

        val sharedPref =
            getSharedPreferences(
                "SesionApp",
                Context.MODE_PRIVATE
            )

        val token =
            sharedPref.getString(
                "apiToken",
                ""
            ) ?: ""

        if (token.isEmpty()) return

        val bearer = "Bearer $token"

        lifecycleScope.launch(Dispatchers.IO) {

            try {

                val dispositivosResponse =
                    RetrofitClient.deviceService
                        .getDispositivos(bearer)

                if (dispositivosResponse.isSuccessful) {

                    val dispositivos =
                        dispositivosResponse.body()?.data
                            ?: emptyList()

                    db.dispositivoDao()
                        .deleteAllDispositivos()

                    db.dispositivoDao()
                        .insertAll(dispositivos)
                }

                val gestosResponse =
                    RetrofitClient.gestureService
                        .getGestos(bearer)

                if (gestosResponse.isSuccessful) {

                    val gestos =
                        gestosResponse.body()?.data
                            ?: emptyList()

                    db.gestoDao()
                        .deleteAllGestos()

                    db.gestoDao()
                        .insertAll(gestos)
                }

            } catch (_: Exception) {
            }
        }
    }

    // ==========================================================
    // NAVEGACION
    // ==========================================================

    private fun abrirPantallaDispositivo() {

        startActivity(
            Intent(
                this,
                DeviceActivity::class.java
            )
        )
    }

    private fun abrirConfiguracionRed() {

        btnConfigurarRed.isEnabled = false

        lifecycleScope.launch {

            try {

                startActivity(
                    Intent(
                        this@HomeActivity,
                        NetworkActivity::class.java
                    )
                )

                delay(1000)

            } finally {

                btnConfigurarRed.isEnabled = true
            }
        }
    }

    private fun abrirMenuPrincipal() {

        if (
            supportFragmentManager
                .findFragmentByTag("MenuBottomSheet")
            != null
        ) return

        val menuSheet =
            MenuBottomSheetDialog(

                onProfileClick = {

                    startActivity(
                        Intent(
                            this,
                            ProfileActivity::class.java
                        )
                    )
                },

                onSettingsClick = {
                    startActivity(
                        Intent(
                            this@HomeActivity,
                            SettingsActivity::class.java
                        )
                    )
                }
            )

        menuSheet.show(
            supportFragmentManager,
            "MenuBottomSheet"
        )
    }

    // ==========================================================
    // HELPERS
    // ==========================================================

    private fun verificarEstadoRedVisual() {

        if (BluetoothController.isConnected) {

            tvRedEstado.text =
                "Hardware Conectado"

            tvRedEstado.setTextColor(
                getColor(R.color.teal_primary)
            )

            iconWifiContainer.setCardBackgroundColor(
                getColor(R.color.teal_primary)
            )
            
            iconWifi.imageTintList = ColorStateList.valueOf(getColor(R.color.white))

        } else {

            tvRedEstado.text =
                "Desconectado"

            tvRedEstado.setTextColor(
                getColor(R.color.text_grey)
            )

            iconWifi.imageTintList =
                ColorStateList.valueOf(
                    getColor(R.color.white)
                )

            iconWifiContainer.setCardBackgroundColor(
                getColor(R.color.text_grey)
            )
        }
    }
}