package com.example.android.voice

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.android.R
import com.example.android.network.RetrofitClient
import com.example.android.network.UsuarioVozConfigDto
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch

class VoiceConfigActivity : AppCompatActivity() {

    private lateinit var swControlVoz: SwitchMaterial
    private lateinit var swConfirmacionHablada: SwitchMaterial
    private lateinit var spTipoVoz: Spinner
    private lateinit var sbVelocidad: SeekBar
    private lateinit var btnProbarVoz: Button
    private lateinit var btnGuardar: Button
    private lateinit var btnBack: ImageButton

    private var usuarioId: Int = -1
    private var token: String = ""
    private var vocesNombres = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_settings)

        // Asumimos que guardas estos en SharedPreferences al iniciar sesión
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        usuarioId = prefs.getInt("USER_ID", -1)
        token = prefs.getString("AUTH_TOKEN", "") ?: ""

        swControlVoz = findViewById(R.id.swControlVoz)
        swConfirmacionHablada = findViewById(R.id.swConfirmacionHablada)
        spTipoVoz = findViewById(R.id.spTipoVoz)
        sbVelocidad = findViewById(R.id.sbVelocidad)
        btnProbarVoz = findViewById(R.id.btnProbarVoz)
        btnGuardar = findViewById(R.id.btnGuardar)
        btnBack = findViewById(R.id.btnBack)

        btnBack.setOnClickListener { finish() }

        TtsManager.inicializar(this)
        cargarVocesTts()
        cargarConfiguracionBackend()

        btnProbarVoz.setOnClickListener {
            val velocidad = sbVelocidad.progress / 10f
            val vozSeleccionada = spTipoVoz.selectedItem as String?

            TtsManager.aplicarVelocidad(velocidad)
            vozSeleccionada?.let { TtsManager.aplicarVoz(it) }
            TtsManager.confirmacionHabladaActivada = true // forzar temporalmente para la prueba
            TtsManager.anunciar("Esta es una prueba de voz.")
        }

        btnGuardar.setOnClickListener {
            guardarConfiguracionBackend()
        }
    }

    private fun cargarVocesTts() {
        // En un caso real, puede que TtsManager tarde unos ms en inicializar y cargar las voces.
        // Aquí lo hacemos simple.
        val voces = TtsManager.getVocesDisponibles()
        vocesNombres = voces.map { it.name }.toMutableList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, vocesNombres)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spTipoVoz.adapter = adapter
    }

    private fun cargarConfiguracionBackend() {
        if (usuarioId == -1 || token.isEmpty()) return

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getVozConfig("Bearer $token", usuarioId)
                if (response.isSuccessful) {
                    val config = response.body()
                    config?.let {
                        swControlVoz.isChecked = it.controlVozActivado
                        swConfirmacionHablada.isChecked = it.confirmacionHabladaActivada
                        it.vozVelocidad?.let { v -> sbVelocidad.progress = (v * 10).toInt() }
                        
                        val vozIdx = vocesNombres.indexOf(it.vozTipoSeleccionado)
                        if (vozIdx >= 0) {
                            spTipoVoz.setSelection(vozIdx)
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@VoiceConfigActivity, "Error cargando configuración", Toast.LENGTH_SHORT).show()
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

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.updateVozConfig("Bearer $token", usuarioId, config)
                if (response.isSuccessful) {
                    // Actualizar TtsManager local
                    TtsManager.confirmacionHabladaActivada = config.confirmacionHabladaActivada
                    config.vozVelocidad?.let { TtsManager.aplicarVelocidad(it) }
                    config.vozTipoSeleccionado?.let { TtsManager.aplicarVoz(it) }

                    // Guardar preferencia local de Control por voz para uso inmediato en cámara
                    val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                    prefs.edit().putBoolean("CONTROL_VOZ_ACTIVADO", config.controlVozActivado).apply()

                    Toast.makeText(this@VoiceConfigActivity, "Configuración guardada", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@VoiceConfigActivity, "Error al guardar", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@VoiceConfigActivity, "Error de red", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // No liberamos TtsManager aquí si queremos que sea un singleton global de la app,
        // pero idealmente se gestiona en la Application class.
    }
}
