package com.example.android

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.android.db.AppDatabase
import com.example.android.network.BluetoothController
import com.example.android.network.RetrofitClient
import com.example.android.ui.components.BottomBarWithFab
import com.example.android.view.Snackbars
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.example.android.ui.DeviceAdapter
import com.example.android.ui.AddDeviceAdapter
import android.app.ActivityManager
import kotlinx.coroutines.delay

class HomeActivity : AppCompatActivity() {

    private lateinit var vistaRaiz: View
    private lateinit var mainHome: MotionLayout
    private lateinit var rvDispositivos: RecyclerView
    private lateinit var deviceAdapter: DeviceAdapter
    private lateinit var db: AppDatabase
    private lateinit var ivProfile: ImageView
    private var isLoggingOut = false

    private lateinit var tvRedEstado: TextView
    private lateinit var iconWifiContainer: MaterialCardView
    private lateinit var iconWifi: ImageView

    private lateinit var configuracion : View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        db = AppDatabase.getDatabase(this)

        inicializarVistas()
        configurarInsets()
        configurarListeners()

        cargarIconosEstaticosEnLinea()

        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (!am.isLowRamDevice) {
            mainHome.post {
                mainHome.transitionToEnd()
            }
        } else {
            mainHome.progress = 1.0f
        }

        if (intent.getBooleanExtra("SHOW_WELCOME", false)) {
            Snackbars.info(mainHome, "Bienvenido", Snackbar.LENGTH_SHORT).show()
        }

        cargarDispositivos()
        configurarBottomBarCompose()
        sincronizarDatosServidor()
    }

    private fun sincronizarDatosServidor() {
        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val token = sharedPref.getString("apiToken", "") ?: ""
        if (token.isEmpty()) return
        val bearer = "Bearer $token"

        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val responseDisp = RetrofitClient.deviceService.getDispositivos(bearer)
                if (responseDisp.isSuccessful) {
                    val apiDevices = responseDisp.body()?.data ?: emptyList()
                    db.dispositivoDao().deleteAllDispositivos()
                    db.dispositivoDao().insertAll(apiDevices)
                }

                val responseGest = RetrofitClient.gestureService.getGestos(bearer)
                if (responseGest.isSuccessful) {
                    val apiGestos = responseGest.body()?.data ?: emptyList()
                    db.gestoDao().deleteAllGestos()
                    db.gestoDao().insertAll(apiGestos)
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun configurarBottomBarCompose() {
        val composeContainer = findViewById<FrameLayout>(R.id.bottom_bar_container)
        val composeView = ComposeView(this).apply {
            setContent {
                BottomBarWithFab(
                    onHomeClick = {
                    },
                    onGesturesClick = {
                        val intent = Intent(this@HomeActivity, Gestos::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        startActivity(intent)
                        overridePendingTransition(0, 0)
                    },
                    onFabClick = {
                        abrirMenuPrincipal()
                    }
                )
            }
        }
        composeContainer.addView(composeView)
    }

    private fun abrirMenuPrincipal() {
        if (supportFragmentManager.findFragmentByTag("MenuBottomSheet") != null) return

        val menuSheet = MenuBottomSheetDialog(
            onProfileClick = {
                val intent = Intent(this@HomeActivity, ProfileActivity::class.java)
                startActivity(intent)
            },
            onSettingsClick = {
                Snackbars.info(findViewById(android.R.id.content), "Configuración próximamente", Snackbar.LENGTH_SHORT).show()
            }
        )
        menuSheet.show(supportFragmentManager, "MenuBottomSheet")
    }

    override fun onResume() {
        super.onResume()
        verificarEstadoRedVisual()
    }

    private fun inicializarVistas() {
        vistaRaiz = findViewById(android.R.id.content)
        mainHome = findViewById(R.id.mainHome)
        rvDispositivos = findViewById(R.id.rvDispositivosHome)
        ivProfile = findViewById(R.id.ivProfile)

        tvRedEstado = findViewById(R.id.tvRedEstado)
        iconWifiContainer = findViewById(R.id.iconWifiContainer)
        iconWifi = findViewById(R.id.iconWifi)
    }

    private fun configurarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(mainHome) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun configurarListeners() {
//        ivProfile.setOnClickListener {
//            abrirMenuPrincipal()
//        }

//        findViewById<View>(R.id.btnEscenas).setOnClickListener {
//            val intent = Intent(this, Gestos::class.java)
//            startActivity(intent)
//        }

        configuracion = findViewById<View>(R.id.btnConfigurarRed);
        configuracion.setOnClickListener {
            configuracion.isEnabled = false
            lifecycleScope.launch {
                try{
                    val intent = Intent(this@HomeActivity, NetworkActivity::class.java)
                    startActivity(intent)
                    delay(1000)
                } catch (e: Exception){
                    Snackbars.error(vistaRaiz,e.message.toString(), Snackbar.LENGTH_SHORT).show()

                }finally {
                    configuracion.isEnabled = true;
                }
            }
        }
    }

    private fun cargarIconosEstaticosEnLinea() {
        LucideLoader.cargarIcono(iconWifi, "wifi")
        findViewById<ImageView>(R.id.ivFlechaDerecha)?.let {
            LucideLoader.cargarIcono(it, "arrow-right")
        }

    }

    private fun cargarDispositivos() {
        deviceAdapter = DeviceAdapter(
            onEditClick = { disp ->
                val intent = Intent(this, DeviceActivity::class.java).apply {
                    putExtra("DISPOSITIVO_ID", disp.id)
                }
                startActivity(intent)
            },
            onDeleteClick = { disp -> },
            onToggleClick = { disp, isChecked ->
                Snackbars.info(mainHome, "${disp.nombre} " + (if(isChecked) "Encendido" else "Apagado"), Snackbar.LENGTH_SHORT).show()
            }
        )

        val addDeviceAdapter = AddDeviceAdapter {
            val intent = Intent(this, DeviceActivity::class.java).apply {
                putExtra("ABRIR_FORMULARIO_AGREGAR", true)
            }
            startActivity(intent)
        }

        val concatAdapter = ConcatAdapter(deviceAdapter, addDeviceAdapter)

        rvDispositivos.layoutManager = GridLayoutManager(this, 2)
        rvDispositivos.adapter = concatAdapter

        lifecycleScope.launch {
            db.dispositivoDao().getAllDispositivos().collectLatest { dispositivos ->
                if (!isLoggingOut) {
                    deviceAdapter.submitList(dispositivos)
                }
                findViewById<TextView>(R.id.tvUsuario)?.let {
                    val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
                    val userName = sharedPref.getString("userName", "Manordomo")
                    it.text = userName
                }
                // Actualizar contador
                // Por ejemplo, se buscará tvStatus pero se requiere un ID
                // Por ahora lo ignoramos o lo actualizamos si tiene un ID.
            }
        }
    }

    private fun logout() {
        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val tokenGuardado = sharedPref.getString("apiToken", "") ?: ""

        isLoggingOut = true

        if (tokenGuardado.isEmpty()) {
            Snackbars.info(vistaRaiz, "Aviso: No hay un token guardado localmente", Toast.LENGTH_LONG).show()
        }

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.logout(tokenGuardado)
                if (response.isSuccessful) {
                    Toast.makeText(this@HomeActivity, "Sesión cerrada en el servidor", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@HomeActivity, "Error API ${response.code()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@HomeActivity, "Error de conexión: ${e.message}", Toast.LENGTH_SHORT).show()
            }

            with(sharedPref.edit()) {
                putBoolean("isLoggedIn", false)
                putString("apiToken", "")
                apply()
            }

            val intent = Intent(this@HomeActivity, MainActivity::class.java).apply {
                putExtra("FROM_LOGOUT", true)
            }
            startActivity(intent)
            finish()
        }
    }
    private fun verificarEstadoRedVisual() {
        if (BluetoothController.isConnected) {
            tvRedEstado.text = "Hardware Conectado"
            tvRedEstado.setTextColor(getColor(R.color.teal_primary))
            iconWifiContainer.setCardBackgroundColor(getColor(R.color.teal_primary))
        } else {
            tvRedEstado.text = "Desconectado"
            tvRedEstado.setTextColor(getColor(R.color.text_grey))
            iconWifi.imageTintList = ColorStateList.valueOf(getColor(R.color.white))
            iconWifiContainer.setCardBackgroundColor(getColor(R.color.text_grey))
        }
    }
}