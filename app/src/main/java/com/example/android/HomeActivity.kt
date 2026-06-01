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
import com.example.android.network.BluetoothController
import com.example.android.network.RetrofitClient
import com.example.android.ui.components.BottomBarWithFab
import com.example.android.view.Snackbars
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay

class HomeActivity : AppCompatActivity() {

    private lateinit var vistaRaiz: View
    private lateinit var mainHome: MotionLayout
    private lateinit var vistaDispositivos: GridLayout
    private lateinit var ivProfile: ImageView

    private lateinit var tvRedEstado: TextView
    private lateinit var iconWifiContainer: MaterialCardView
    private lateinit var iconWifi: ImageView

    private lateinit var configuracion : View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)


        inicializarVistas()
        configurarInsets()
        configurarListeners()

        cargarIconosEstaticosEnLinea()

        mainHome.post {
            mainHome.transitionToEnd()
        }

        if (intent.getBooleanExtra("SHOW_WELCOME", false)) {
            Snackbars.info(mainHome, "Bienvenido", Snackbar.LENGTH_SHORT).show()
        }

        cargarDispositivos()
        configurarBottomBarCompose()
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
        vistaDispositivos = findViewById(R.id.vistaDispositivos)
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
        val inflater = LayoutInflater.from(this)
        vistaDispositivos.removeAllViews()

        val cardAdd = inflater.inflate(R.layout.item_add_device, vistaDispositivos, false)
        cardAdd.setOnClickListener {
            Snackbars.info(it, "Abriendo panel de dispositivos", Snackbar.LENGTH_SHORT).show()
            val intent = Intent(this, DeviceActivity::class.java)
            startActivity(intent)
        }
        vistaDispositivos.addView(cardAdd)

        agregarTarjetaDispositivo(inflater, "Bombillas", "Encendidas 2", "lightbulb", true)
        agregarTarjetaDispositivo(inflater, "Smart TV", "Panasonic", "tv", false)
        agregarTarjetaDispositivo(inflater, "Wi-Fi Router", "TP Link", "router", true)
        agregarTarjetaDispositivo(inflater, "Wi-Fi Router", "TP Link", "router", true)
        agregarTarjetaDispositivo(inflater, "Wi-Fi Router", "TP Link", "router", true)
        agregarTarjetaDispositivo(inflater, "Wi-Fi Router", "TP Link", "router", true)
        agregarTarjetaDispositivo(inflater, "Bombillas", "Encendidas 2", "lightbulb", true)
        agregarTarjetaDispositivo(inflater, "Smart TV", "Panasonic", "tv", false)
        agregarTarjetaDispositivo(inflater, "Wi-Fi Router", "TP Link", "router", true)
        agregarTarjetaDispositivo(inflater, "Wi-Fi Router", "TP Link", "router", true)
        agregarTarjetaDispositivo(inflater, "Wi-Fi Router", "TP Link", "router", true)
        agregarTarjetaDispositivo(inflater, "Wi-Fi Router", "TP Link", "router", true)
        agregarTarjetaDispositivo(inflater, "Wi-Fi Router", "TP Link", "router", true)

        vistaDispositivos.scheduleLayoutAnimation()
    }

    private fun agregarTarjetaDispositivo(
        inflater: LayoutInflater,
        nombre: String,
        estado: String,
        lucideIconName: String,
        estaEncendido: Boolean
    ) {
        val card = inflater.inflate(R.layout.item_device, vistaDispositivos, false)

        val tvName = card.findViewById<TextView>(R.id.tvDeviceName)
        val tvStatus = card.findViewById<TextView>(R.id.tvDeviceStatus)
        val ivIcon = card.findViewById<ImageView>(R.id.ivDeviceIcon)
        val sw = card.findViewById<SwitchMaterial>(R.id.switchDevice)
        val materialCard = card.findViewById<MaterialCardView>(R.id.deviceCard)
        val iconBg = card.findViewById<MaterialCardView>(R.id.iconBg)

        tvName.text = nombre
        tvStatus.text = estado

        LucideLoader.cargarIcono(ivIcon, lucideIconName)

        sw.isChecked = estaEncendido

        actualizarEstiloTarjeta(materialCard, iconBg, tvName, tvStatus, sw, estaEncendido)

        sw.setOnCheckedChangeListener { _, isChecked ->
            actualizarEstiloTarjeta(materialCard, iconBg, tvName, tvStatus, sw, isChecked)
            val mensaje = if (isChecked) "$nombre Encendido" else "$nombre Apagado"
            Snackbars.info(mainHome, mensaje, Snackbar.LENGTH_SHORT).show()
        }

        vistaDispositivos.addView(card)
    }
    private fun actualizarEstiloTarjeta(
        card: MaterialCardView,
        iconBg: MaterialCardView,
        tvName: TextView,
        tvStatus: TextView,
        sw: SwitchMaterial,
        estaEncendido: Boolean
    ) {
        val trackColor = if (estaEncendido) R.color.teal_primary else R.color.teal_card
        iconBg.setCardBackgroundColor(getColor(R.color.teal_primary))
        sw.trackTintList = getColorStateList(trackColor)
        sw.thumbTintList = getColorStateList(android.R.color.white)
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