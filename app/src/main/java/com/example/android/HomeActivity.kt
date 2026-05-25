package com.example.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.android.network.BluetoothController
import com.example.android.network.RetrofitClient
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import kotlin.jvm.java

class HomeActivity : AppCompatActivity() {

    private lateinit var vistaDispositivos: GridLayout
    private lateinit var vistaRaiz: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)
        vistaRaiz = findViewById(android.R.id.content)

        val mainHome = findViewById<androidx.constraintlayout.motion.widget.MotionLayout>(R.id.mainHome)
        ViewCompat.setOnApplyWindowInsetsListener(mainHome) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Ejecutar animación con un pequeño retraso para asegurar fluidez
        mainHome.post {
            mainHome.transitionToEnd()
        }

        if (intent.getBooleanExtra("SHOW_WELCOME", false)) {
            Snackbars.info(mainHome, "Bienvenido", Snackbar.LENGTH_SHORT).show()
        }

        vistaDispositivos = findViewById(R.id.vistaDispositivos)

        val ivProfile = findViewById<ImageView>(R.id.ivProfile)
        ivProfile.setOnClickListener {
            showProfileMenu(it)
        }

        findViewById<View>(R.id.btnEscenas).setOnClickListener {
            val intent = Intent(this, GestureActivity::class.java)
            startActivity(intent)
        }

        findViewById<View>(R.id.btnConfigurarRed).setOnClickListener {
            val intent = Intent(this@HomeActivity, NetworkActivity::class.java)
            startActivity(intent)
        }

        cargarDispositivos()
    }

    private fun cargarDispositivos() {
        val inflater = LayoutInflater.from(this)

        vistaDispositivos.removeAllViews()

        agregarTarjetaDispositivo(inflater, "Bombillas", "Encendidas 2", android.R.drawable.ic_lock_power_off, true)
        agregarTarjetaDispositivo(inflater, "Smart TV", "Panasonic", android.R.drawable.ic_menu_slideshow, false)
        agregarTarjetaDispositivo(inflater, "Wi-Fi Router", "TP Link", android.R.drawable.stat_sys_phone_call, true)

        val cardAdd = inflater.inflate(R.layout.item_add_device, vistaDispositivos, false)
        cardAdd.setOnClickListener {
            Snackbars.info(it, "Abriendo panel de dispositivos", Snackbar.LENGTH_SHORT).show()
            val intent = Intent(this, DeviceActivity::class.java)
            startActivity(intent)
        }
        vistaDispositivos.addView(cardAdd)

        vistaDispositivos.scheduleLayoutAnimation()
    }

    private fun agregarTarjetaDispositivo(
        inflater: LayoutInflater,
        nombre: String,
        estado: String,
        iconRes: Int,
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
        ivIcon.setImageResource(iconRes)
        sw.isChecked = estaEncendido

        actualizarEstiloTarjeta(materialCard, iconBg, tvName, tvStatus, sw, estaEncendido)

        sw.setOnCheckedChangeListener { _, isChecked ->
            actualizarEstiloTarjeta(materialCard, iconBg, tvName, tvStatus, sw, isChecked)
            val mensaje = if (isChecked) "$nombre Encendido" else "$nombre Apagado"
            val mainHome = findViewById<View>(R.id.mainHome)
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
        if (estaEncendido) {
            iconBg.setCardBackgroundColor(getColor(R.color.teal_primary))
            sw.trackTintList = getColorStateList(R.color.teal_primary)
            sw.thumbTintList = getColorStateList(android.R.color.white)
        } else {
            iconBg.setCardBackgroundColor(getColor(R.color.teal_primary))
            sw.trackTintList = getColorStateList(R.color.teal_card)
            sw.thumbTintList = getColorStateList(android.R.color.white)
        }
    }

    private fun showProfileMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.profile_menu, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_logout -> {
                    logout()
                    true
                }
                R.id.action_settings -> {
                    Snackbars.info(view, "Configuración próximamente", Snackbar.LENGTH_SHORT).show()
                    true
                }
                R.id.action_profile -> {
                    val intent = Intent(this@HomeActivity, ProfileActivity::class.java)
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun logout() {
        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val tokenGuardado = sharedPref.getString("apiToken", "") ?: ""

        if (tokenGuardado.isEmpty()) {
            Snackbars.info(vistaRaiz, "Aviso: No hay un token guardado localmente", Toast.LENGTH_LONG)
                .show()
        }

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.logout(tokenGuardado)

                if (response.isSuccessful) {
                    Toast.makeText(
                        this@HomeActivity,
                        "Sesión cerrada en el servidor",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val codigoError = response.code()
                    val cuerpoError = response.errorBody()?.string() ?: ""
                    Toast.makeText(
                        this@HomeActivity,
                        "Error API $codigoError: $cuerpoError",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@HomeActivity,
                    "Error de conexión: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }

            with(sharedPref.edit()) {
                putBoolean("isLoggedIn", false)
                putString("apiToken", "")
                apply()
            }

            val intent = Intent(this@HomeActivity, MainActivity::class.java)
            intent.putExtra("FROM_LOGOUT", true)
            startActivity(intent)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        verificarEstadoRedVisual()
    }

    private fun verificarEstadoRedVisual() {
        // Obtenemos las referencias de la tarjeta en activity_home.xml
        val tvRedEstado = findViewById<TextView>(R.id.tvRedEstado)
        val iconWifiContainer = findViewById<com.google.android.material.card.MaterialCardView>(R.id.iconWifiContainer)

        if (BluetoothController.isConnected) {
            // El socket está abierto
            tvRedEstado.text = "Hardware Conectado"
            tvRedEstado.setTextColor(getColor(R.color.teal_primary))
            iconWifiContainer.setCardBackgroundColor(getColor(R.color.teal_primary))
        } else {
            // El socket está cerrado
            tvRedEstado.text = "Desconectado"
            tvRedEstado.setTextColor(getColor(R.color.text_grey)) // O usa Color.RED
            iconWifiContainer.setCardBackgroundColor(getColor(R.color.text_grey))
        }
    }
}
