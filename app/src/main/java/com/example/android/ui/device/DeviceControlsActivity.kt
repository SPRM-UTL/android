package com.example.android.ui.device

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ImageButton
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
import com.example.android.actions.DeviceActionManager
import com.example.android.network.BluetoothController
import com.example.android.network.RetrofitClient
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.widget.Toast
import android.media.AudioManager
import android.content.Context
import android.view.KeyEvent

class DeviceControlsActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private var deviceId: Int = -1
    private var currentDevice: Dispositivo? = null

    // Vistas
    private lateinit var tvControlDeviceName: TextView
    private lateinit var btnBackControls: ImageButton

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
        btnBackControls = findViewById(R.id.btnBackControls)

        tvPowerState = findViewById(R.id.tvPowerState)
        switchPower = findViewById(R.id.switchPower)

        cardVolumeControl = findViewById(R.id.cardVolumeControl)
        sliderVolume = findViewById(R.id.sliderVolume)
        tvVolumeValue = findViewById(R.id.tvVolumeValue)
    }

    private fun configurarInsets() {
        val root = findViewById<View>(R.id.mainDeviceControls)
        val cardBack = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardBack)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Aplica el padding superior al ConstraintLayout interno de la tarjeta
            cardBack?.getChildAt(0)?.setPadding(0, systemBars.top, 0, 0)

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
            currentDevice?.let { dev ->
                DeviceActionManager.ejecutarAccion(this, dev, DeviceActionManager.ACTION_POWER, isChecked)

                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            RetrofitClient.deviceService.toggleAparato(dev.id, isChecked)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        sliderVolume.addOnChangeListener { _, value, _ ->
            tvVolumeValue.text = "${value.toInt()}%"
            currentDevice?.let { dev ->
                DeviceActionManager.ejecutarAccion(this, dev, DeviceActionManager.ACTION_VOLUME, value)
            }
        }
    }

    private fun cargarDispositivo() {
        lifecycleScope.launch {
            currentDevice = db.dispositivoDao().getDispositivoById(deviceId)
            currentDevice?.let { dispositivo ->
                tvControlDeviceName.text = dispositivo.nombre ?: "Dispositivo"

                val tipo = (dispositivo.tipo ?: "")
                val tipoLower = tipo.lowercase()

                if (tipoLower.contains("bocina") || tipoLower.contains("audio") || tipoLower.contains("speaker") || tipoLower.contains("audífono") || tipoLower.contains("audifono")) {
                    cardVolumeControl.visibility = View.VISIBLE
                    actualizarSliderDesdeSistema()
                } else {
                    cardVolumeControl.visibility = View.GONE
                }

                validarConexionYConectar(dispositivo)
            }
        }
    }

    private fun actualizarSliderDesdeSistema() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val percent = (currVol.toFloat() / maxVol) * 100f

        sliderVolume.value = percent
        tvVolumeValue.text = "${percent.toInt()}%"
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val handled = super.onKeyDown(keyCode, event)
            sliderVolume.postDelayed({
                actualizarSliderDesdeSistema()
            }, 100)
            return handled
        }
        return super.onKeyDown(keyCode, event)
    }

    @SuppressLint("MissingPermission")
    private suspend fun validarConexionYConectar(dispositivo: Dispositivo) {
        try {
            val sharedPref = getSharedPreferences("SesionApp", MODE_PRIVATE)
            val token = sharedPref.getString("apiToken", "") ?: ""

            val response = RetrofitClient.deviceService.getTiposAparato("Bearer $token")
            if (response.isSuccessful && response.body()?.success == true) {
                val tipos = response.body()?.data ?: emptyList()
                val tipoObj = tipos.find { it.nombreTipo == dispositivo.tipo }

                if (tipoObj?.soportaBluetooth == true) {
                    val mac = dispositivo.macBluetooth
                    if (!mac.isNullOrEmpty()) {
                        withContext(Dispatchers.IO) {
                            try {
                                val adapter = BluetoothAdapter.getDefaultAdapter()
                                val remoteDevice = adapter.getRemoteDevice(mac)
                                val exito = BluetoothController.connectDevice(remoteDevice)
                                withContext(Dispatchers.Main) {
                                    if (exito) {
                                        Toast.makeText(this@DeviceControlsActivity, "Conectado por Bluetooth", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}