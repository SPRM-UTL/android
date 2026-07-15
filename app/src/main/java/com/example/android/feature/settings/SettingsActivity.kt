package com.example.android.feature.settings
import com.example.android.core.voice.VoiceConfigActivity

import com.example.android.R

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.android.core.view.Snackbars
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var vistaRaiz: View
    private lateinit var sharedPref: SharedPreferences

    private lateinit var swNotif : SwitchMaterial
    private lateinit var swDark : SwitchMaterial
    private lateinit var swBio : SwitchMaterial
    private lateinit var containerBio : View

    private lateinit var biometricManager : BiometricManager
    private var biometriaDisponible : Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = true

        setContentView(R.layout.activity_settings)

        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        val mainSettings = findViewById<View>(R.id.mainSettings)
        ViewCompat.setOnApplyWindowInsetsListener(mainSettings) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom + ime.bottom)
            val cardBack = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardBack)
            cardBack?.getChildAt(0)?.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        inicializarVistas()
        configurarEstilosDinamicosSwitches()
        configurarOpciones()
    }

    private fun inicializarVistas(){
        vistaRaiz = findViewById(android.R.id.content)
        sharedPref = getSharedPreferences("SesionApp", MODE_PRIVATE)

        swNotif = findViewById(R.id.switchNotifications)
        swDark = findViewById(R.id.switchDarkMode)
        swBio = findViewById(R.id.switchBiometric)
        containerBio = findViewById(R.id.containerBiometric)

        biometricManager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK

        biometriaDisponible = biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun configurarEstilosDinamicosSwitches() {
        val tealPrimary = ContextCompat.getColor(this, R.color.teal_primary)
        val grisInactivo = Color.parseColor("#757575")
        val trackActivo = Color.parseColor("#80008080")
        val trackInactivo = Color.parseColor("#E0E0E0")

        val thumbStates = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
            intArrayOf(tealPrimary, grisInactivo)
        )

        val trackStates = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
            intArrayOf(trackActivo, trackInactivo)
        )

        val switches = listOf(swNotif, swDark, swBio)
        switches.forEach { switch ->
            switch.thumbTintList = thumbStates
            switch.trackTintList = trackStates
        }

        swNotif.setOnCheckedChangeListener { _, isChecked ->
            swNotif.text = if (isChecked) "Notificaciones: Activadas" else "Notificaciones: Desactivadas"
            val msg = if (isChecked) "Notificaciones activadas" else "Notificaciones desactivadas"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // Corrección del Modo Oscuro (Próximamente) sin alterar estados ficticios
        swDark.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Toast.makeText(this, "Modo oscuro próximamente", Toast.LENGTH_SHORT).show()
                swDark.post {
                    swDark.isChecked = false
                }
            }
            swDark.text = "Modo Oscuro (Próximamente)"
        }

        swBio.setOnCheckedChangeListener { _, isChecked ->
            swBio.text = if (isChecked) "Entrada Biométrica: Activada" else "Entrada Biométrica: Desactivada"
            sharedPref.edit().putBoolean("biometricEnabled", isChecked).apply()
            val msg = if (isChecked) "Biometría habilitada" else "Biometría inhabilitada"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun configurarOpciones() {
        if (biometriaDisponible) {
            containerBio.visibility = View.VISIBLE
            val bioHabilitada = sharedPref.getBoolean("biometricEnabled", true)
            swBio.isChecked = bioHabilitada
            swBio.text = if (bioHabilitada) "Entrada Biométrica: Activada" else "Entrada Biométrica: Desactivada"
        } else {
            containerBio.visibility = View.GONE
            swBio.isChecked = false
            swBio.isEnabled = false
            swBio.text = "Entrada Biométrica: No compatible"
        }

        swNotif.text = if (swNotif.isChecked) "Notificaciones: Activadas" else "Notificaciones: Desactivadas"

        // Inicialización segura del Modo Oscuro
        swDark.text = "Modo Oscuro (Próximamente)"
        swDark.isChecked = false

        findViewById<View>(R.id.btnAbout).setOnClickListener {
            Toast.makeText(this, "Manordomo v1.0.2 - Sistema de Control Domótico", Toast.LENGTH_LONG).show()
        }

        findViewById<View>(R.id.btnVoiceConfig).setOnClickListener {
            startActivity(Intent(this, com.example.android.core.voice.VoiceConfigActivity::class.java))
        }
    }
}
