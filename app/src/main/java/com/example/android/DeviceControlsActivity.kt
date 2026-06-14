package com.example.android

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.android.db.AppDatabase
import com.example.android.db.Dispositivo
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch

class DeviceControlsActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private var deviceId: Int = -1
    private var currentDevice: Dispositivo? = null

    // Vistas
    private lateinit var tvControlDeviceName: TextView
    private lateinit var ivDeviceIcon: ImageView
    private lateinit var btnBackControls: ImageView
    
    private lateinit var tvPowerState: TextView
    private lateinit var switchPower: SwitchMaterial
    
    private lateinit var cardVolumeControl: MaterialCardView
    private lateinit var sliderVolume: Slider
    private lateinit var tvVolumeValue: TextView

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

        setContentView(R.layout.activity_device_controls)
        
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
        ivDeviceIcon = findViewById(R.id.ivDeviceIcon)
        btnBackControls = findViewById(R.id.btnBackControls)
        
        tvPowerState = findViewById(R.id.tvPowerState)
        switchPower = findViewById(R.id.switchPower)
        
        cardVolumeControl = findViewById(R.id.cardVolumeControl)
        sliderVolume = findViewById(R.id.sliderVolume)
        tvVolumeValue = findViewById(R.id.tvVolumeValue)
    }

    private fun configurarInsets() {
        val root = findViewById<View>(R.id.mainDeviceControls)
        val header = findViewById<View>(R.id.headerControls)
        
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.setPadding(header.paddingLeft, systemBars.top + 16, header.paddingRight, 16)
            root.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun configurarEventos() {
        btnBackControls.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        switchPower.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                tvPowerState.text = "Encendido"
                tvPowerState.setTextColor(ContextCompat.getColor(this, R.color.teal_primary))
                switchPower.trackTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.teal_primary))
            } else {
                tvPowerState.text = "Apagado"
                tvPowerState.setTextColor(Color.parseColor("#757575"))
                switchPower.trackTintList = ColorStateList.valueOf(Color.parseColor("#E0E0E0"))
            }
            // Aquí en el futuro se llamaría al BluetoothController para enviar el comando
        }

        sliderVolume.addOnChangeListener { _, value, _ ->
            tvVolumeValue.text = "${value.toInt()}%"
            // Aquí en el futuro se llamaría al BluetoothController para ajustar el volumen
        }
    }

    private fun cargarDispositivo() {
        lifecycleScope.launch {
            currentDevice = db.dispositivoDao().getDispositivoById(deviceId)
            currentDevice?.let { dispositivo ->
                tvControlDeviceName.text = dispositivo.nombre ?: "Dispositivo"
                
                // Configuración dinámica por tipo
                val tipo = (dispositivo.tipo ?: "").lowercase()
                
                if (tipo.contains("bocina") || tipo.contains("audio") || tipo.contains("speaker")) {
                    cardVolumeControl.visibility = View.VISIBLE
                    // ivDeviceIcon.setImageResource(R.drawable.speaker) // Opcional
                } else if (tipo.contains("ventilador") || tipo.contains("fan")) {
                    cardVolumeControl.visibility = View.GONE
                    // ivDeviceIcon.setImageResource(R.drawable.fan) // Opcional
                } else {
                    // Por defecto (Luces, etc)
                    cardVolumeControl.visibility = View.GONE
                }
            }
        }
    }
}
