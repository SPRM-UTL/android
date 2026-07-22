package com.example.android.feature.device

import com.example.android.core.network.api.AparatoEstadoResponse

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
import com.example.android.core.db.init.AppDatabase
import com.example.android.core.db.models.Dispositivo
import com.example.android.core.network.client.RetrofitClient
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VentiladorInteligenteActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private var deviceId: Int = -1
    private var currentDevice: Dispositivo? = null

    private lateinit var tvControlDeviceName: TextView
    private lateinit var btnBackControls: ImageButton
    private lateinit var tvCorriente: TextView
    private lateinit var tvPotencia: TextView
    private lateinit var tvEnergia: TextView

    private lateinit var switchVelocidad1: SwitchMaterial
    private lateinit var switchVelocidad2: SwitchMaterial
    private lateinit var switchVelocidad3: SwitchMaterial

    private lateinit var cardVelocidad1: MaterialCardView
    private lateinit var cardVelocidad2: MaterialCardView
    private lateinit var cardVelocidad3: MaterialCardView

    private lateinit var tvStatusVelocidad1: TextView
    private lateinit var tvStatusVelocidad2: TextView
    private lateinit var tvStatusVelocidad3: TextView

    private var pollingJob: Job? = null
    private var isLoadingState = false

    private val accentColor by lazy { ContextCompat.getColor(this, R.color.teal_primary) }
    private val blueColor by lazy { Color.parseColor("#1565C0") }

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

        setContentView(R.layout.activity_ventilador_inteligente)

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

        switchVelocidad1 = findViewById(R.id.switchVelocidad1)
        switchVelocidad2 = findViewById(R.id.switchVelocidad2)
        switchVelocidad3 = findViewById(R.id.switchVelocidad3)

        cardVelocidad1 = findViewById(R.id.cardVelocidad1)
        cardVelocidad2 = findViewById(R.id.cardVelocidad2)
        cardVelocidad3 = findViewById(R.id.cardVelocidad3)

        tvStatusVelocidad1 = findViewById(R.id.tvStatusVelocidad1)
        tvStatusVelocidad2 = findViewById(R.id.tvStatusVelocidad2)
        tvStatusVelocidad3 = findViewById(R.id.tvStatusVelocidad3)
    }

    private fun configurarInsets() {
        val root = findViewById<View>(R.id.mainVentilador)
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

        val switches = listOf(switchVelocidad1, switchVelocidad2, switchVelocidad3)
        val cards = listOf(cardVelocidad1, cardVelocidad2, cardVelocidad3)
        val statusTexts = listOf(tvStatusVelocidad1, tvStatusVelocidad2, tvStatusVelocidad3)

        switches.forEachIndexed { index, switchMaterial ->
            val contacto = index + 1
            switchMaterial.setOnCheckedChangeListener { _, isChecked ->
                if (!isLoadingState) {
                    if (isChecked) {
                        desactivarOtrasVelocidades(index)
                        toggleContacto(contacto, true)
                    } else {
                        toggleContacto(contacto, false)
                    }
                }
                actualizarEstiloTarjeta(cards[index], statusTexts[index], switchMaterial, isChecked)
            }
        }
    }

    private fun desactivarOtrasVelocidades(exceptIndex: Int) {
        val switches = listOf(switchVelocidad1, switchVelocidad2, switchVelocidad3)
        val cards = listOf(cardVelocidad1, cardVelocidad2, cardVelocidad3)
        val statusTexts = listOf(tvStatusVelocidad1, tvStatusVelocidad2, tvStatusVelocidad3)

        switches.forEachIndexed { index, switchMaterial ->
            if (index != exceptIndex && switchMaterial.isChecked) {
                isLoadingState = true
                switchMaterial.isChecked = false
                isLoadingState = false
                actualizarEstiloTarjeta(cards[index], statusTexts[index], switchMaterial, false)
                toggleContacto(index + 1, false)
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
            card.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#E3F2FD")))
            card.setStrokeColor(ColorStateList.valueOf(blueColor))
            statusText.text = "Activo"
            statusText.setTextColor(blueColor)
            switchMaterial.trackTintList = ColorStateList.valueOf(blueColor)
        } else {
            card.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#F9FBFD")))
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
                tvControlDeviceName.text = dispositivo.nombre ?: "Ventilador Inteligente"
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

    private fun actualizarUIConEstado(estado: AparatoEstadoResponse) {
        isLoadingState = true

        switchVelocidad1.isChecked = estado.estadoEncendido == true
        switchVelocidad2.isChecked = estado.estadoEncendido2 == true
        switchVelocidad3.isChecked = estado.estadoEncendido3 == true

        actualizarEstiloTarjeta(cardVelocidad1, tvStatusVelocidad1, switchVelocidad1, switchVelocidad1.isChecked)
        actualizarEstiloTarjeta(cardVelocidad2, tvStatusVelocidad2, switchVelocidad2, switchVelocidad2.isChecked)
        actualizarEstiloTarjeta(cardVelocidad3, tvStatusVelocidad3, switchVelocidad3, switchVelocidad3.isChecked)

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
                    Toast.makeText(this@VentiladorInteligenteActivity, "Error al enviar comando", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@VentiladorInteligenteActivity, "Error de conexión", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
    }
}
