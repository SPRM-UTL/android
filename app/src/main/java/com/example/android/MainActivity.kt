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
import android.widget.TextView
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
import com.example.android.network.LoginRequest
import com.example.android.network.RetrofitClient
import com.example.android.view.CustomDialog
import com.example.android.view.Snackbars
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

            lifecycleScope.launch {
                delay(2000)
                splashProvider.remove()

                val motionLayout = findViewById<MotionLayout>(R.id.motionLayout)
                motionLayout.transitionToEnd()
            }
        }

        db = AppDatabase.getDatabase(this)
        setContentView(R.layout.activity_main)

        CustomDialog.loadingDialog(this)

        val btnLoginHuella = findViewById<MaterialButton>(R.id.btnLoginHuella)
        val biometricManager = BiometricManager.from(this)

        if (intent.getBooleanExtra("FROM_LOGOUT", false)) {
            findViewById<MotionLayout>(R.id.motionLayout).post {
                findViewById<MotionLayout>(R.id.motionLayout).transitionToEnd()
            }
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                db.dispositivoDao().deleteAllDispositivos()
                db.gestoDao().deleteAllGestos()
            }
        }

        if(biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS){
            btnLoginHuella.visibility = View.VISIBLE
        }else{
            btnLoginHuella.visibility = View.GONE
        }

        lifecycleScope.launch {
            usuarioPruebaBd()

            val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
            val estaLogueado = sharedPref.getBoolean("isLoggedIn", false)

            if (estaLogueado) {
                irAHome()
                return@launch
            }

            delay(500)
            manteniendoSplash = false
        }

        val etUsuario = findViewById<EditText>(R.id.etUsuario)
        val etContrasena = findViewById<EditText>(R.id.etContrasena)
        val btnLoginTradicional = findViewById<Button>(R.id.btnLogin)

        btnLoginTradicional.setOnClickListener { view ->
            val usuarioInput = etUsuario.text.toString()
            val contrasenaInput = etContrasena.text.toString()

            if (usuarioInput.isEmpty() || contrasenaInput.isEmpty()) {
                Snackbars.info(view, "Por favor llena ambos campos", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    CustomDialog.loadingDialog(this@MainActivity)

                    CustomDialog.showDialog(
                        "Verificando",
                        "Por favor espera...",
                        CustomDialog.DialogType.LOADING
                    )
                    val peticion = LoginRequest(correo = usuarioInput, contrasenia = contrasenaInput)
                    val response = RetrofitClient.apiService.login(peticion)

                    if (response.isSuccessful && response.body() != null && response.body()!!.success) {
                        val tokenApi = response.body()!!.data.token
                        val idApi = response.body()!!.data.id

                        guardarSesionExitosa(view, tokenApi, idApi)
                        CustomDialog.dismissDialog()
                    } else {
                        CustomDialog.dismissDialog()
                        Snackbars.error(view, "Credenciales incorrectas", Snackbar.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    CustomDialog.dismissDialog()
                    Snackbars.error(view, "Error de red: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }

        executor = ContextCompat.getMainExecutor(this)

        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    
                    val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
                    val tokenExistente = sharedPref.getString("apiToken", "")
                    
                    if (!tokenExistente.isNullOrEmpty()) {
                        guardarSesionExitosa(findViewById(android.R.id.content))
                    } else {
                        Snackbars.warning(findViewById(android.R.id.content), "Primero inicia sesión con tu contraseña", Snackbar.LENGTH_LONG).show()
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Snackbars.info(findViewById(android.R.id.content), "Error biométrico: $errString", Snackbar.LENGTH_SHORT).show()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Snackbars.info(findViewById(android.R.id.content), "Huella no reconocida", Snackbar.LENGTH_SHORT).show()
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

        val tvRegistrarse = findViewById<TextView>(R.id.tvRegistrarse)
        tvRegistrarse.setOnClickListener {
            val intent = Intent(this@MainActivity, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    private fun guardarSesionExitosa(view: View, token: String = "", userId: Int = -1) {
        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("isLoggedIn", true)

            if (token.isNotEmpty()) {
                putString("apiToken", token)
            }
            if (userId != -1) {
                putInt("userId", userId)
            }
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