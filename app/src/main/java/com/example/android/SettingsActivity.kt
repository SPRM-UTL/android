package com.example.android

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.compose.material3.Snackbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.android.view.Snackbars
import com.example.android.view.cambiarColorStatusBar
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
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
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
        configurarOpciones()
    }

    private fun inicializarVistas(){
        vistaRaiz          = findViewById(android.R.id.content)
        sharedPref = getSharedPreferences("SesionApp", MODE_PRIVATE)

        swNotif = findViewById<SwitchMaterial>(R.id.switchNotifications)
        swDark = findViewById<SwitchMaterial>(R.id.switchDarkMode)
        swBio = findViewById<SwitchMaterial>(R.id.switchBiometric)
        containerBio = findViewById<View>(R.id.containerBiometric)

        biometricManager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.BIOMETRIC_WEAK

        biometriaDisponible = biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    }

    private fun configurarOpciones() {

        if (biometriaDisponible) {
            containerBio.visibility = View.VISIBLE
            val bioHabilitada = sharedPref.getBoolean("biometricEnabled", true)
            swBio.isChecked = bioHabilitada
        } else {
            containerBio.visibility = View.GONE
            swBio.isChecked = false
            swBio.isEnabled = false
        }

        swNotif.setOnCheckedChangeListener { _, isChecked ->
            val msg = if (isChecked) "Notificaciones activadas" else "Notificaciones desactivadas"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        swDark.setOnCheckedChangeListener { _, _ ->
            Toast.makeText(this, "Modo oscuro próximamente", Toast.LENGTH_SHORT).show()
            swDark.isChecked = false
        }

        swBio.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.edit().putBoolean("biometricEnabled", isChecked).apply()
            val msg = if (isChecked) "Biometría habilitada" else "Biometría inhabilitada"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.btnAbout).setOnClickListener {
            if(!biometriaDisponible){
                Snackbars.error(vistaRaiz,"No es compatible tu dispositivo")
            }else{
                Toast.makeText(this, "Manordomo v1.0.2 - Sistema de Control Domótico", Toast.LENGTH_LONG).show()
            }

        }

        findViewById<View>(R.id.btnVoiceConfig).setOnClickListener {
            startActivity(android.content.Intent(this, com.example.android.voice.VoiceConfigActivity::class.java))
        }
    }


}
