package com.example.android.feature.device
import com.example.android.core.network.AparatoEstadoResponse

import com.example.android.R

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.android.core.db.AppDatabase
import com.example.android.core.db.Dispositivo
import com.example.android.core.network.RetrofitClient
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MultiSocketActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private var deviceId: Int = -1
    private var currentDevice: Dispositivo? = null

    // Vistas principales de navegación y consumo
    private lateinit var tvControlDeviceName: TextView
    private lateinit var btnBackControls: ImageButton
    private lateinit var tvCorriente: TextView
    private lateinit var tvPotencia: TextView
    private lateinit var tvEnergia: TextView

    // Componentes del Grid de Contactos
    private lateinit var switchPower1: SwitchMaterial
    private lateinit var switchPower2: SwitchMaterial
    private lateinit var switchPower3: SwitchMaterial
    private lateinit var switchPower4: SwitchMaterial

    private lateinit var cardContacto1: MaterialCardView
    private lateinit var cardContacto2: MaterialCardView
    private lateinit var cardContacto3: MaterialCardView
    private lateinit var cardContacto4: MaterialCardView

    private lateinit var tvStatusContacto1: TextView
    private lateinit var tvStatusContacto2: TextView
    private lateinit var tvStatusContacto3: TextView
    private lateinit var tvStatusContacto4: TextView

    private var pollingJob: Job? = null
    private var isLoadingState = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.WHITE
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        setContentView(R.layout.activity_multi_socket)

        db = AppDatabase.getDatabase(this)
        deviceId = intent.getIntExtra("EXTRA_DEVICE_ID", -1)

        inicializarVistas()
        configurarInsets()
        configurarEventos()

        if (deviceId != -1) {
            cargarDispositivo()
        } else {
            finish()
        }
    }

    private fun inicializarVistas() {
        tvControlDeviceName = findViewById(R.id.tvControlDeviceName)
        btnBackControls = findViewById(R.id.btnBackControls)

        tvCorriente = findViewById(R.id.tvCorriente)
        tvPotencia = findViewById(R.id.tvPotencia)
        tvEnergia = findViewById(R.id.tvEnergia)

        // Switches
        switchPower1 = findViewById(R.id.switchPower1)
        switchPower2 = findViewById(R.id.switchPower2)
        switchPower3 = findViewById(R.id.switchPower3)
        switchPower4 = findViewById(R.id.switchPower4)

        // Tarjetas Contenedoras
        cardContacto1 = findViewById(R.id.cardContacto1)
        cardContacto2 = findViewById(R.id.cardContacto2)
        cardContacto3 = findViewById(R.id.cardContacto3)
        cardContacto4 = findViewById(R.id.cardContacto4)

        // Textos de Estado Dinámicos
        tvStatusContacto1 = findViewById(R.id.tvStatusContacto1)
        tvStatusContacto2 = findViewById(R.id.tvStatusContacto2)
        tvStatusContacto3 = findViewById(R.id.tvStatusContacto3)
        tvStatusContacto4 = findViewById(R.id.tvStatusContacto4)
    }

    private fun configurarInsets() {
        val root = findViewById<View>(R.id.mainMultiSocket)
        val cardBack = findViewById<MaterialCardView>(R.id.cardBack)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            cardBack?.getChildAt(0)?.setPadding(0, systemBars.top, 0, 0)
            root.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun configurarEventos() {
        btnBackControls.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        val switches = listOf(switchPower1, switchPower2, switchPower3, switchPower4)
        val cards = listOf(cardContacto1, cardContacto2, cardContacto3, cardContacto4)
        val statusTexts = listOf(tvStatusContacto1, tvStatusContacto2, tvStatusContacto3, tvStatusContacto4)

        switches.forEachIndexed { index, switchMaterial ->
            val contacto = index + 1
            switchMaterial.setOnCheckedChangeListener { _, isChecked ->
                if (!isLoadingState) {
                    toggleContacto(contacto, isChecked)
                }
                // Aplica el rediseño estético al interactuar localmente
                actualizarEstiloTarjeta(cards[index], statusTexts[index], switchMaterial, isChecked)
            }
        }
    }

    private fun actualizarEstiloTarjeta(
        card: MaterialCardView,
        statusText: TextView,
        switchMaterial: SwitchMaterial,
        isEnabled: Boolean
    ) {
        if (isEnabled) {
            card.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#E0F2F1"))) // Teal ligero de activación
            card.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.teal_primary)))
            statusText.text = "Activo"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.teal_primary))
            switchMaterial.trackTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.teal_primary))
        } else {
            card.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#F9FBFD"))) // Gris suave de apagado
            card.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#EAEAEA")))
            statusText.text = "Apagado"
            statusText.setTextColor(Color.parseColor("#888888"))
            switchMaterial.trackTintList = ColorStateList.valueOf(Color.parseColor("#E0E0E0"))
        }
    }

    private fun cargarDispositivo() {
        lifecycleScope.launch {
            currentDevice = withContext(Dispatchers.IO) {
                db.dispositivoDao().getDispositivoById(deviceId)
            }

            currentDevice?.let { dispositivo ->
                tvControlDeviceName.text = dispositivo.nombre ?: "Regleta Inteligente"
                iniciarPollingEstado()
            }
        }
    }

    private fun iniciarPollingEstado() {
        pollingJob?.cancel()
        pollingJob = lifecycleScope.launch {
            while (isActive) {
                obtenerEstadoActual()
                delay(3000)
            }
        }
    }

    private suspend fun obtenerEstadoActual() {
        try {
            val response = RetrofitClient.deviceService.getAparatoEstado(deviceId)
            if (response.isSuccessful) {
                response.body()?.let { estado ->
                    actualizarUIConEstado(estado)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun actualizarUIConEstado(estado: com.example.android.core.network.AparatoEstadoResponse) {
        isLoadingState = true

        switchPower1.isChecked = estado.estadoEncendido == true
        switchPower2.isChecked = estado.estadoEncendido2 == true
        switchPower3.isChecked = estado.estadoEncendido3 == true
        switchPower4.isChecked = estado.estadoEncendido4 == true

        // Forzar la actualización visual de las tarjetas según la respuesta del backend
        actualizarEstiloTarjeta(cardContacto1, tvStatusContacto1, switchPower1, switchPower1.isChecked)
        actualizarEstiloTarjeta(cardContacto2, tvStatusContacto2, switchPower2, switchPower2.isChecked)
        actualizarEstiloTarjeta(cardContacto3, tvStatusContacto3, switchPower3, switchPower3.isChecked)
        actualizarEstiloTarjeta(cardContacto4, tvStatusContacto4, switchPower4, switchPower4.isChecked)

        tvCorriente.text = String.format("%.2f A", estado.corrienteA ?: 0f)
        tvPotencia.text = String.format("%.2f W", estado.potenciaW ?: 0f)
        tvEnergia.text = String.format("%.2f Wh", estado.energiaAcumuladaWh ?: 0f)

        isLoadingState = false
    }

    private fun toggleContacto(contacto: Int, encendido: Boolean) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.deviceService.toggleAparatoContacto(
                    deviceId,
                    contacto,
                    encendido
                )

                if (!response.isSuccessful) {
                    Toast.makeText(this@MultiSocketActivity, "Error al enviar comando", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MultiSocketActivity, "Error de conexión", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
    }
}
