package com.example.android.voice

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.Spinner
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.android.R
import com.example.android.network.RetrofitClient
import com.example.android.network.UsuarioVozConfigDto
import com.example.android.view.CustomDialog
import com.example.android.view.Snackbars
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class VoiceConfigActivity : AppCompatActivity() {

    private lateinit var swControlVoz: SwitchMaterial
    private lateinit var swConfirmacionHablada: SwitchMaterial
    private lateinit var spTipoVoz: Spinner
    private lateinit var sbVelocidad: SeekBar
    private lateinit var btnProbarVoz: MaterialButton
    private lateinit var btnGuardar: MaterialButton
    private lateinit var btnBack: ImageButton
    private lateinit var mainVoiceConfig: ConstraintLayout
    private lateinit var vistaRaiz: View

    private var usuarioId: Int = -1
    private var token: String = ""
    private var vocesNombres = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configuración inmersiva Edge-to-Edge corporativo
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = Color.TRANSPARENT

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightNavigationBars = true
            isAppearanceLightStatusBars = false
        }

        setContentView(R.layout.activity_voice_settings)

        mainVoiceConfig = findViewById(R.id.mainVoiceConfig)
        vistaRaiz = findViewById(android.R.id.content)

        // Ajuste fluido de paddings por la barra de estado
        ViewCompat.setOnApplyWindowInsetsListener(mainVoiceConfig) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)

            val cardBack = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardBack)
            cardBack?.getChildAt(0)?.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        // Obtención de sesión local
        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        usuarioId = sharedPref.getInt("userId", -1)
        token = sharedPref.getString("apiToken", "") ?: ""

        inicializarComponentes()
        configurarEstilosDinamicosSwitches()

        TtsManager.inicializar(this)
        cargarVocesTts()

        if (usuarioId != -1 && token.isNotEmpty()) {
            cargarConfiguracionBackend()
        } else {
            Snackbars.error(vistaRaiz, "Error de sesión local", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun inicializarComponentes() {
        swControlVoz = findViewById(R.id.swControlVoz)
        swConfirmacionHablada = findViewById(R.id.swConfirmacionHablada)
        spTipoVoz = findViewById(R.id.spTipoVoz)
        sbVelocidad = findViewById(R.id.sbVelocidad)
        btnProbarVoz = findViewById(R.id.btnProbarVoz)
        btnGuardar = findViewById(R.id.btnGuardar)
        btnBack = findViewById(R.id.btnBack)

        btnBack.setOnClickListener { finish() }

        btnProbarVoz.setOnClickListener {
            val velocidad = sbVelocidad.progress / 10f
            val vozSeleccionada = spTipoVoz.selectedItem as String?

            TtsManager.aplicarVelocidad(velocidad)
            vozSeleccionada?.let { TtsManager.aplicarVoz(it) }
            TtsManager.confirmacionHabladaActivada = true
            TtsManager.anunciar("Esta es una prueba de voz configurada con éxito.")
        }

        btnGuardar.setOnClickListener {
            guardarConfiguracionBackend()
        }
    }

    /**
     * Aplica selectores de estados (ColorStateLists) de manera programática para evitar
     * problemas de renderizado en fondos claros y cambia el texto del Switch dinámicamente.
     */
    private fun configurarEstilosDinamicosSwitches() {
        val tealPrimary = ContextCompat.getColor(this, R.color.teal_primary)
        val grisInactivo = Color.parseColor("#757575")
        val trackActivo = Color.parseColor("#80008080") // Teal translúcido
        val trackInactivo = Color.parseColor("#E0E0E0")

        val thumbStates = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
            intArrayOf(tealPrimary, grisInactivo)
        )

        val trackStates = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
            intArrayOf(trackActivo, trackInactivo)
        )

        // Aplicación a Switch 1: Control por Voz
        swControlVoz.thumbTintList = thumbStates
        swControlVoz.trackTintList = trackStates
        swControlVoz.setOnCheckedChangeListener { _, isChecked ->
            swControlVoz.text = if (isChecked) "Control por voz: Activado" else "Control por voz: Desactivado"
        }

        // Aplicación a Switch 2: Confirmación Hablada
        swConfirmacionHablada.thumbTintList = thumbStates
        swConfirmacionHablada.trackTintList = trackStates
        swConfirmacionHablada.setOnCheckedChangeListener { _, isChecked ->
            swConfirmacionHablada.text = if (isChecked) "Confirmación hablada: Activada" else "Confirmación hablada: Desactivada"
        }
    }

    private fun cargarVocesTts() {
        val voces = TtsManager.getVocesDisponibles()
        vocesNombres = voces.map { it.name }.toMutableList()

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, vocesNombres)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spTipoVoz.adapter = adapter
    }

    private fun cargarConfiguracionBackend() {
        btnGuardar.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getVozConfig("Bearer $token", usuarioId)
                if (response.isSuccessful) {
                    val config = response.body()
                    config?.let {
                        swControlVoz.isChecked = it.controlVozActivado
                        swConfirmacionHablada.isChecked = it.confirmacionHabladaActivada

                        // Forzar refresco inicial de textos dinámicos
                        swControlVoz.text = if (it.controlVozActivado) "Control por voz: Activado" else "Control por voz: Desactivado"
                        swConfirmacionHablada.text = if (it.confirmacionHabladaActivada) "Confirmación hablada: Activada" else "Confirmación hablada: Desactivada"

                        it.vozVelocidad?.let { v -> sbVelocidad.progress = (v * 10).toInt() }

                        val vozIdx = vocesNombres.indexOf(it.vozTipoSeleccionado)
                        if (vozIdx >= 0) {
                            spTipoVoz.setSelection(vozIdx)
                        }
                    }
                }
                btnGuardar.isEnabled = true
            } catch (e: Exception) {
                Snackbars.error(vistaRaiz, "Error al recuperar datos del servidor", Snackbar.LENGTH_SHORT).show()
                btnGuardar.isEnabled = true
            }
        }
    }

    private fun guardarConfiguracionBackend() {
        if (usuarioId == -1 || token.isEmpty()) return

        val config = UsuarioVozConfigDto(
            controlVozActivado = swControlVoz.isChecked,
            confirmacionHabladaActivada = swConfirmacionHablada.isChecked,
            vozTipoSeleccionado = spTipoVoz.selectedItem as String?,
            vozVelocidad = sbVelocidad.progress / 10f,
            vozIdioma = "es-MX"
        )

        btnGuardar.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.updateVozConfig("Bearer $token", usuarioId, config)
                if (response.isSuccessful) {
                    TtsManager.confirmacionHabladaActivada = config.confirmacionHabladaActivada
                    config.vozVelocidad?.let { TtsManager.aplicarVelocidad(it) }
                    config.vozTipoSeleccionado?.let { TtsManager.aplicarVoz(it) }

                    // Preferencias locales consistentes
                    val prefs = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("CONTROL_VOZ_ACTIVADO", config.controlVozActivado).apply()

                    Snackbars.success(vistaRaiz, "Configuración actualizada correctamente", Snackbar.LENGTH_SHORT).show()
                    delay(1200)
                    finish()
                } else {
                    Snackbars.error(vistaRaiz, "Error de validación del servidor", Snackbar.LENGTH_SHORT).show()
                    btnGuardar.isEnabled = true
                }
            } catch (e: Exception) {
                Snackbars.error(vistaRaiz, "Error de red: Sin conexión con el servicio", Snackbar.LENGTH_SHORT).show()
                btnGuardar.isEnabled = true
            }
        }
    }
}