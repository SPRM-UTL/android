package com.example.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.android.db.AppDatabase
import com.example.android.db.Casa
import com.example.android.db.Habitacion
import com.example.android.network.ApiHandler
import com.example.android.network.RetrofitClient
import com.example.android.view.Snackbars
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InitialSetupActivity : AppCompatActivity() {

    private lateinit var layoutStep1: LinearLayout
    private lateinit var layoutStep2: LinearLayout
    private lateinit var etNombreCasa: TextInputEditText
    private lateinit var etNombreHabitacion: TextInputEditText
    private lateinit var btnAnterior: MaterialButton
    private lateinit var btnSiguiente: MaterialButton
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var db: AppDatabase

    private var currentStep = 1
    private var nombreCasa = ""
    private var nombreHabitacion = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        
        setContentView(R.layout.activity_initial_setup)
        
        db = AppDatabase.getDatabase(this)

        val root = findViewById<View>(R.id.rootInitialSetup)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        layoutStep1 = findViewById(R.id.layoutStep1)
        layoutStep2 = findViewById(R.id.layoutStep2)
        etNombreCasa = findViewById(R.id.etNombreCasa)
        etNombreHabitacion = findViewById(R.id.etNombreHabitacion)
        btnAnterior = findViewById(R.id.btnAnterior)
        btnSiguiente = findViewById(R.id.btnSiguiente)
        loadingOverlay = findViewById(R.id.loadingOverlay)

        updateUI()

        btnSiguiente.setOnClickListener {
            if (currentStep == 1) {
                nombreCasa = etNombreCasa.text.toString().trim()
                if (nombreCasa.isEmpty()) {
                    etNombreCasa.error = "Ingresa un nombre"
                    return@setOnClickListener
                }
                currentStep = 2
                updateUI()
            } else if (currentStep == 2) {
                nombreHabitacion = etNombreHabitacion.text.toString().trim()
                if (nombreHabitacion.isEmpty()) {
                    etNombreHabitacion.error = "Ingresa un nombre"
                    return@setOnClickListener
                }
                crearCasaYHabitacion()
            }
        }

        btnAnterior.setOnClickListener {
            if (currentStep == 2) {
                currentStep = 1
                updateUI()
            }
        }
    }

    private fun updateUI() {
        if (currentStep == 1) {
            layoutStep1.visibility = View.VISIBLE
            layoutStep2.visibility = View.GONE
            btnAnterior.visibility = View.INVISIBLE
            btnSiguiente.text = "Siguiente"
        } else {
            layoutStep1.visibility = View.GONE
            layoutStep2.visibility = View.VISIBLE
            btnAnterior.visibility = View.VISIBLE
            btnSiguiente.text = "Finalizar"
        }
    }

    private fun crearCasaYHabitacion() {
        loadingOverlay.visibility = View.VISIBLE
        btnAnterior.isEnabled = false
        btnSiguiente.isEnabled = false
        
        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val token = sharedPref.getString("apiToken", "") ?: ""
        val userId = sharedPref.getInt("userId", 0)

        lifecycleScope.launch {
            try {
                // 1. Crear Casa
                val nuevaCasa = Casa(id = 0, nombre = nombreCasa, skUsuarioId = userId)
                val casaResponse = RetrofitClient.casaService.createCasa("Bearer $token", nuevaCasa)
                
                if (casaResponse.isSuccessful && casaResponse.body()?.data != null) {
                    val casaCreada = casaResponse.body()?.data!!
                    
                    withContext(Dispatchers.IO) {
                        db.casaDao().insert(casaCreada)
                    }

                    // 2. Crear Habitación
                    val nuevaHab = Habitacion(id = 0, nombre = nombreHabitacion, skCasaId = casaCreada.id)
                    val habResponse = RetrofitClient.habitacionService.createHabitacion("Bearer $token", nuevaHab)
                    
                    if (habResponse.isSuccessful && habResponse.body()?.data != null) {
                        withContext(Dispatchers.IO) {
                            db.habitacionDao().insert(habResponse.body()?.data!!)
                        }
                        
                        // Todo correcto, ir al Home
                        loadingOverlay.visibility = View.GONE
                        val intent = Intent(this@InitialSetupActivity, HomeActivity::class.java)
                        intent.putExtra("SHOW_WELCOME", true)
                        startActivity(intent)
                        finish()
                    } else {
                        mostrarError("Error al crear la habitación. Intenta de nuevo.")
                    }
                } else {
                    mostrarError("Error al crear la casa. Intenta de nuevo.")
                }
            } catch (e: Exception) {
                mostrarError("Error de conexión: ${e.message}")
            }
        }
    }
    
    private fun mostrarError(mensaje: String) {
        loadingOverlay.visibility = View.GONE
        btnAnterior.isEnabled = true
        btnSiguiente.isEnabled = true
        Snackbars.error(findViewById(R.id.rootInitialSetup), mensaje, Snackbar.LENGTH_LONG).show()
    }
}
