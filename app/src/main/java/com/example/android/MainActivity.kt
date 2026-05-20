package com.example.android

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Bundle
import androidx.biometric.BiometricManager
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.android.db.AppDatabase
import com.example.android.db.Usuario
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
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
        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor = android.graphics.Color.TRANSPARENT

        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = false


        splashScreen.setKeepOnScreenCondition {
            manteniendoSplash
        }

        splashScreen.setOnExitAnimationListener { splashProvider ->
            val iconView = splashProvider.iconView
            if (iconView is ImageView) {
                (iconView.drawable as? Animatable)?.start()
            }
            // Retrasar la salida un poco para ver la animación
            lifecycleScope.launch {
                delay(2000)
                splashProvider.remove()
            }
        }

        db = AppDatabase.getDatabase(this)
        setContentView(R.layout.activity_main)

        val rootView = findViewById<View>(android.R.id.content)

        val btnLoginHuella = findViewById<MaterialButton>(R.id.btnLoginHuella)

        val biometricManager = BiometricManager.from(this)

        if(biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)== BiometricManager.BIOMETRIC_SUCCESS){
            btnLoginHuella.visibility = View.VISIBLE
        }else{
            btnLoginHuella.visibility = View.GONE
        }
        val motionLayout = findViewById<MotionLayout>(R.id.motionLayout)
        val imgLogo = findViewById<ImageView>(R.id.imgLogo)

        lifecycleScope.launch {
            usuarioPruebaBd()

            val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
            val estaLogueado = sharedPref.getBoolean("isLoggedIn", false)

            if (estaLogueado) {
                irAHome()
                return@launch
            }
            manteniendoSplash = false

            delay(2000)

            motionLayout.transitionToEnd()
        }

        val etUsuario = findViewById<EditText>(R.id.etUsuario)
        val etContrasena = findViewById<EditText>(R.id.etContrasena)
        val btnLoginTradicional = findViewById<Button>(R.id.btnLogin)


        btnLoginTradicional.setOnClickListener {
            val usuarioInput = etUsuario.text.toString()
            val contrasenaInput = etContrasena.text.toString()

            if (usuarioInput.isEmpty() || contrasenaInput.isEmpty()) {
                Snackbar.make(it, "Por favor llena ambos campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val userEncontrado = db.usuarioDao().login(usuarioInput, contrasenaInput)

                if (userEncontrado != null) {
                    guardarSesionExitosa(it)
                } else {
                    Snackbar.make(it, "Usuario o contraseña incorrectos", Snackbar.LENGTH_SHORT).show()
                }
            }
        }

        executor = ContextCompat.getMainExecutor(this)

        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    guardarSesionExitosa(rootView)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Snackbar.make(rootView, "Error biométrico: $errString", Toast.LENGTH_SHORT).show()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Snackbar.make(rootView, "Huella no reconocida", Toast.LENGTH_SHORT).show()
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

    private fun guardarSesionExitosa(view : View) {
        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("isLoggedIn", true)
            apply()
        }
        irAHome(true)
    }

    private fun irAHome(mostrarBienvenida: Boolean = false) {
        val intent = Intent(this, HomeActivity::class.java)
        if (mostrarBienvenida) {
            intent.putExtra("SHOW_WELCOME", true)
        }
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
