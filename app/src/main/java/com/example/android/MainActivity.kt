package com.example.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.example.android.db.AppDatabase
import com.example.android.db.Usuario
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity() {

    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private lateinit var db: AppDatabase

    private var manteniendoSplash = true

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition {
            manteniendoSplash
        }
        db = AppDatabase.getDatabase(this)

        lifecycleScope.launch {
            usuarioPruebaBd()

            val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
            val estaLogueado = sharedPref.getBoolean("isLoggedIn", false)

            if (estaLogueado) {
                irAHome()
                return@launch
            }

            manteniendoSplash = false
        }
        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val estaLogueado = sharedPref.getBoolean("isLoggedIn", false)

        if (estaLogueado) {
            irAHome()
            finish()
            return
        }

        setContentView(R.layout.activity_main)



        usuarioPruebaBd()

        val etUsuario = findViewById<EditText>(R.id.etUsuario)
        val etContrasena = findViewById<EditText>(R.id.etContrasena)
        val btnLoginTradicional = findViewById<Button>(R.id.btnLogin)
        val btnLoginHuella = findViewById<Button>(R.id.btnLoginHuella)

        btnLoginTradicional.setOnClickListener {
            val usuarioInput = etUsuario.text.toString()
            val contrasenaInput = etContrasena.text.toString()

            if (usuarioInput.isEmpty() || contrasenaInput.isEmpty()) {
                Toast.makeText(this, "Por favor llena ambos campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val userEncontrado = db.usuarioDao().login(usuarioInput, contrasenaInput)

                if (userEncontrado != null) {
                    guardarSesionExitosa()
                } else {
                    Toast.makeText(this@MainActivity, "Usuario o contraseña incorrectos", Toast.LENGTH_SHORT).show()
                }
            }
        }

        executor = ContextCompat.getMainExecutor(this)

        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    guardarSesionExitosa()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(applicationContext, "Error biométrico: $errString", Toast.LENGTH_SHORT).show()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(applicationContext, "Huella no reconocida", Toast.LENGTH_SHORT).show()
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Inicio de sesión con huella")
            .setSubtitle("Toca el sensor de huella para ingresar")
            .setNegativeButtonText("Cancelar")
            .build()

        btnLoginHuella.setOnClickListener {
            biometricPrompt.authenticate(promptInfo)
        }
    }

    private fun guardarSesionExitosa() {
        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("isLoggedIn", true)
            apply()
        }
        Toast.makeText(this, "Bienvenido", Toast.LENGTH_SHORT).show()
        irAHome()
    }

    private fun irAHome() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun usuarioPruebaBd() {
        lifecycleScope.launch {
            if (db.usuarioDao().contarUsuarios() == 0) {
                val usuarioPrueba = Usuario(nombreUsuario = "admin", contrasena = "1234")
                db.usuarioDao().insertarUsuario(usuarioPrueba)
            }
        }
    }
}