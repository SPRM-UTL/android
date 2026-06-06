package com.example.android

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.android.view.cambiarColorStatusBar
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = true

        setContentView(R.layout.activity_settings)

        cambiarColorStatusBar(R.color.teal_primary, true)

        val mainSettings = findViewById<View>(R.id.mainSettings)
        ViewCompat.setOnApplyWindowInsetsListener(mainSettings) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, ime.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        configurarOpciones()
    }

    private fun configurarOpciones() {
        val swNotif = findViewById<SwitchMaterial>(R.id.switchNotifications)
        val swDark = findViewById<SwitchMaterial>(R.id.switchDarkMode)
        val swBio = findViewById<SwitchMaterial>(R.id.switchBiometric)

        swNotif.setOnCheckedChangeListener { _, isChecked ->
            val msg = if (isChecked) "Notificaciones activadas" else "Notificaciones desactivadas"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        swDark.setOnCheckedChangeListener { _, isChecked ->
            Toast.makeText(this, "Modo oscuro próximamente", Toast.LENGTH_SHORT).show()
            swDark.isChecked = false
        }

        swBio.setOnCheckedChangeListener { _, isChecked ->
            val msg = if (isChecked) "Biometría habilitada" else "Biometría inhabilitada"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.btnAbout).setOnClickListener {
            Toast.makeText(this, "Manordomo v1.0.2 - Sistema de Control Domótico", Toast.LENGTH_LONG).show()
        }
    }
}
