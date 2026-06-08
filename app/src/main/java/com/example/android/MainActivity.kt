package com.example.android


import android.content.Context
import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.util.Patterns
import androidx.biometric.BiometricManager
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.example.android.db.AppDatabase
import com.example.android.network.LoginRequest
import com.example.android.network.RetrofitClient
import com.example.android.view.CustomDialog
import com.example.android.view.Snackbars
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executor


class MainActivity : AppCompatActivity() {

    // ==========================================================
    // VARIABLES
    // ==========================================================

    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    private lateinit var db: AppDatabase

    private lateinit var etUsuario: TextInputEditText
    private lateinit var etContrasena: TextInputEditText

    private lateinit var txtInputUsuario: TextInputLayout
    private lateinit var txtInputContrasena: TextInputLayout

    private lateinit var btnLoginTradicional: Button
    private lateinit var tvRegistrarse: TextView

    private var manteniendoSplash = true

    // ==========================================================
    // LIFECYCLE
    // ==========================================================

    override fun onCreate(savedInstanceState: Bundle?) {

        configurarSplash()

        super.onCreate(savedInstanceState)

        configurarVentana()

        setContentView(R.layout.activity_main)


        /* 
        startActivity(
            Intent(
                this,
                EspConfigActivity::class.java
            )
        )
        */


        inicializar()

        configurarUI()

        configurarEventos()

        verificarSesionActiva()
    }

    // ==========================================================
    // INICIALIZACION
    // ==========================================================

    private fun inicializar() {

        db = AppDatabase.getDatabase(this)

        inicializarVistas()

        inicializarBiometria()
    }

    private fun inicializarVistas() {

        etUsuario = findViewById(R.id.etUsuario)
        etContrasena = findViewById(R.id.etContrasena)

        txtInputUsuario = findViewById(R.id.txtInputUsuario)
        txtInputContrasena = findViewById(R.id.txtInputContrasena)

        btnLoginTradicional = findViewById(R.id.btnLogin)

        tvRegistrarse = findViewById(R.id.tvRegistrarse)
    }

    // ==========================================================
    // CONFIGURACION UI
    // ==========================================================

    private fun configurarUI() {

        CustomDialog.loadingDialog(this)

        procesarLogout()
    }

    // ==========================================================
    // EVENTOS
    // ==========================================================

    private fun configurarEventos() {

        btnLoginTradicional.setOnClickListener {
            iniciarSesionTradicional()
        }

        tvRegistrarse.setOnClickListener {
            abrirRegistro()
        }

        val motionLayout = findViewById<MotionLayout>(R.id.motionLayout)

        ViewCompat.setOnApplyWindowInsetsListener(motionLayout) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())

            if (isKeyboardVisible) {
                motionLayout.setTransition(R.id.compactTransition)
                motionLayout.transitionToEnd()
            } else {
                if (motionLayout.progress > 0.9f) {
                    motionLayout.setTransition(R.id.compactTransition)
                    motionLayout.transitionToStart()
                }
            }

            v.setPadding(bars.left, bars.top, bars.right, ime.bottom)
            insets
        }

        // Tocar fuera para cerrar teclado
        val cerrarTecladoLogin = View.OnClickListener {
            val focus = currentFocus
            if (focus is TextInputEditText) {
                focus.clearFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(focus.windowToken, 0)
            }
        }

        motionLayout.setOnClickListener(cerrarTecladoLogin)
        findViewById<View>(R.id.scrollContent).setOnClickListener(cerrarTecladoLogin)

        // Enter en contraseña para cerrar teclado
        etContrasena.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                v.clearFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
            }
            false
        }
    }

    // ==========================================================
    // LOGIN
    // ==========================================================

    private fun iniciarSesionTradicional() {

        if (!validarEntradas()) return

        val usuario =
            etUsuario.text.toString().trim()

        val password =
            etContrasena.text.toString().trim()

        lifecycleScope.launch {
            realizarLogin(usuario, password)
        }
    }

    private suspend fun realizarLogin(
        usuario: String,
        password: String
    ) {

        try {

            mostrarCarga()

            val peticion = LoginRequest(
                correo = usuario,
                contrasenia = password
            )

            val response =
                RetrofitClient.apiService.login(peticion)

            if (
                response.isSuccessful &&
                response.body()?.success == true
            ) {

                guardarSesionExitosa(
                    findViewById(android.R.id.content),
                    response.body()!!.data.token,
                    response.body()!!.data.id
                )

            } else {

                Snackbars.error(
                    findViewById(android.R.id.content),
                    "Credenciales incorrectas",
                    Snackbar.LENGTH_SHORT
                ).show()
            }

        } catch (e: Exception) {

            Snackbars.error(
                findViewById(android.R.id.content),
                "Error de red: ${e.message}",
                Snackbar.LENGTH_LONG
            ).show()

        } finally {

            CustomDialog.dismissDialog()
        }
    }

    // ==========================================================
    // BIOMETRIA
    // ==========================================================

    private fun inicializarBiometria() {

        executor = ContextCompat.getMainExecutor(this)

        biometricPrompt =
            BiometricPrompt(
                this,
                executor,
                biometricCallback
            )

        promptInfo =
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Inicio de sesión con huella")
                .setSubtitle("Toca el sensor de huella para ingresar")
                .setNegativeButtonText("Cancelar")
                .build()
    }

    private val biometricCallback =
        object : BiometricPrompt.AuthenticationCallback() {

            override fun onAuthenticationSucceeded(
                result: BiometricPrompt.AuthenticationResult
            ) {
                irAHome()
            }

            override fun onAuthenticationError(
                errorCode: Int,
                errString: CharSequence
            ) {

            }

            override fun onAuthenticationFailed() {

                Snackbars.info(
                    findViewById(android.R.id.content),
                    "Huella no reconocida",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }

    // ==========================================================
    // VALIDACIONES
    // ==========================================================

    private fun validarEntradas(): Boolean {

        val usuarioInput =
            etUsuario.text.toString().trim()

        val contrasenaInput =
            etContrasena.text.toString().trim()

        var isValid = true

        if (usuarioInput.isEmpty()) {
            txtInputUsuario.error = "El correo no puede estar vacío"
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(usuarioInput).matches()) {
            txtInputUsuario.error = "Ingresa un correo válido"
            isValid = false
        } else {
            txtInputUsuario.error = null
        }

        if (contrasenaInput.isEmpty()) {
            txtInputContrasena.error = "La contraseña no puede estar vacía"
            isValid = false
        } else {
            txtInputContrasena.error = null
        }

        return isValid
    }

    // ==========================================================
    // SESION
    // ==========================================================

    private fun verificarSesionActiva() {

        lifecycleScope.launch {

            val sharedPref =
                getSharedPreferences(
                    "SesionApp",
                    Context.MODE_PRIVATE
                )

            val estaLogueado =
                sharedPref.getBoolean(
                    "isLoggedIn",
                    false
                )

            if (estaLogueado) {

                manteniendoSplash = false

                delay(300)

                intentarLoginBiometrico()

                return@launch
            }
            delay(500)

            manteniendoSplash = false
        }
    }

    private fun guardarSesionExitosa(
        view: View,
        token: String = "",
        userId: Int = -1
    ) {

        val sharedPref =
            getSharedPreferences(
                "SesionApp",
                Context.MODE_PRIVATE
            )

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

    // ==========================================================
    // NAVEGACION
    // ==========================================================

    private fun abrirRegistro() {

        startActivity(
            Intent(
                this,
                RegisterActivity::class.java
            )
        )
    }

    private fun irAHome(
        mostrarBienvenida: Boolean = false
    ) {

        val intent =
            Intent(
                this,
                HomeActivity::class.java
            )

        if (mostrarBienvenida) {
            intent.putExtra(
                "SHOW_WELCOME",
                true
            )
        }

        startActivity(intent)

        finish()
    }

    // ==========================================================
    // HELPERS
    // ==========================================================

    private fun configurarSplash() {

        val splashScreen = installSplashScreen()

        splashScreen.setKeepOnScreenCondition {
            manteniendoSplash
        }

        splashScreen.setOnExitAnimationListener { splashProvider ->

            lifecycleScope.launch {

                delay(2000)

                splashProvider.remove()

                findViewById<MotionLayout>(
                    R.id.motionLayout
                )?.transitionToEnd()
            }
        }
    }
    private fun configurarVentana() {

        WindowCompat.setDecorFitsSystemWindows(
            window,
            false
        )

        window.statusBarColor =
            android.graphics.Color.TRANSPARENT

        window.navigationBarColor =
            android.graphics.Color.TRANSPARENT

        WindowInsetsControllerCompat(
            window,
            window.decorView
        ).isAppearanceLightStatusBars = false

        WindowInsetsControllerCompat(
            window,
            window.decorView
        ).isAppearanceLightNavigationBars = false
    }
    private fun intentarLoginBiometrico() {

        val biometricManager =
            BiometricManager.from(this)

        val biometriaDisponible =
            biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            ) == BiometricManager.BIOMETRIC_SUCCESS

        if (!biometriaDisponible) return

        val sharedPref =
            getSharedPreferences(
                "SesionApp",
                Context.MODE_PRIVATE
            )

        val token =
            sharedPref.getString(
                "apiToken",
                ""
            )

        if (!token.isNullOrEmpty()) {

            biometricPrompt.authenticate(promptInfo)
        }
    }
    private fun procesarLogout() {

        if (!intent.getBooleanExtra(
                "FROM_LOGOUT",
                false
            )
        ) return

        findViewById<MotionLayout>(
            R.id.motionLayout
        ).post {

            findViewById<MotionLayout>(
                R.id.motionLayout
            ).transitionToEnd()
        }

        lifecycleScope.launch(
            kotlinx.coroutines.Dispatchers.IO
        ) {

            db.dispositivoDao()
                .deleteAllDispositivos()

            db.gestoDao()
                .deleteAllGestos()
        }
    }

    private fun mostrarCarga() {

        CustomDialog.loadingDialog(this)

        CustomDialog.showDialog(
            "Verificando",
            "Por favor espera...",
            CustomDialog.DialogType.LOADING
        )
    }
}
