package com.example.android

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
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
import com.example.android.db.AppDatabase
import com.example.android.db.Dispositivo
import com.example.android.network.RetrofitClient
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

    // Vistas
    private lateinit var tvControlDeviceName: TextView
    private lateinit var ivDeviceIcon: ImageView
    private lateinit var btnBackControls: ImageView
    
    private lateinit var tvCorriente: TextView
    private lateinit var tvPotencia: TextView
    private lateinit var tvEnergia: TextView

    private lateinit var switchPower1: SwitchMaterial
    private lateinit var switchPower2: SwitchMaterial
    private lateinit var switchPower3: SwitchMaterial
    private lateinit var switchPower4: SwitchMaterial

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
        ivDeviceIcon = findViewById(R.id.ivDeviceIcon)
        btnBackControls = findViewById(R.id.btnBackControls)
        
        tvCorriente = findViewById(R.id.tvCorriente)
        tvPotencia = findViewById(R.id.tvPotencia)
        tvEnergia = findViewById(R.id.tvEnergia)

        switchPower1 = findViewById(R.id.switchPower1)
        switchPower2 = findViewById(R.id.switchPower2)
        switchPower3 = findViewById(R.id.switchPower3)
        switchPower4 = findViewById(R.id.switchPower4)
    }

    private fun configurarInsets() {
        val root = findViewById<View>(R.id.mainMultiSocket)
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

        val switches = listOf(switchPower1, switchPower2, switchPower3, switchPower4)
        
        switches.forEachIndexed { index, switchMaterial ->
            val contacto = index + 1
            switchMaterial.setOnCheckedChangeListener { _, isChecked ->
                if (!isLoadingState) {
                    toggleContacto(contacto, isChecked)
                }
                
                // Actualizar color
                if (isChecked) {
                    switchMaterial.trackTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.teal_primary))
                } else {
                    switchMaterial.trackTintList = ColorStateList.valueOf(Color.parseColor("#E0E0E0"))
                }
            }
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
                delay(3000) // Poll every 3 seconds for telemetry
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

    private fun actualizarUIConEstado(estado: com.example.android.network.AparatoEstadoResponse) {
        isLoadingState = true
        
        switchPower1.isChecked = estado.estadoEncendido == true
        switchPower2.isChecked = estado.estadoEncendido2 == true
        switchPower3.isChecked = estado.estadoEncendido3 == true
        switchPower4.isChecked = estado.estadoEncendido4 == true

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
