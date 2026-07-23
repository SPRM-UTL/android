package com.example.android.feature.main
import com.example.android.feature.ai.presentation.activities.PermisosActivity

import com.example.android.R

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Patterns
import androidx.biometric.BiometricManager
import android.view.View
import android.widget.Button
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
import com.example.android.core.db.init.AppDatabase
import com.example.android.core.network.client.LoginRequest
import com.example.android.core.network.client.RetrofitClient
import com.example.android.core.view.CustomDialog
import com.example.android.core.view.Snackbars
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import com.example.android.feature.home.HomeActivity
import com.example.android.feature.auth.InitialSetupActivity
import com.example.android.feature.auth.RegisterActivity


class MainActivity : AppCompatActivity() {

    // ==========================================================
    // VARIABLES
    // ==========================================================

    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private lateinit var db: AppDatabase
    private lateinit var motionLayout: MotionLayout          // ← referencia cacheada
    private lateinit var etUsuario: TextInputEditText
    private lateinit var etContrasena: TextInputEditText
    private lateinit var txtInputUsuario: TextInputLayout
    private lateinit var txtInputContrasena: TextInputLayout
    private lateinit var btnLoginTradicional: Button
    private lateinit var btnBiometricLogin: MaterialButton
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

        motionLayout          = findViewById(R.id.motionLayout)   // ← se inicializa aquí
        etUsuario             = findViewById(R.id.etUsuario)
        etContrasena          = findViewById(R.id.etContrasena)
        txtInputUsuario       = findViewById(R.id.txtInputUsuario)
        txtInputContrasena    = findViewById(R.id.txtInputContrasena)
        btnLoginTradicional   = findViewById(R.id.btnLogin)
        btnBiometricLogin     = findViewById(R.id.btnBiometricLogin)
        tvRegistrarse         = findViewById(R.id.tvRegistrarse)
    }

    // ==========================================================
    // CONFIGURACION UI
    // ==========================================================

    private fun configurarUI() {

        CustomDialog.loadingDialog(this)

        procesarLogout()

        actualizarVisibilidadBiometrica()
    }

    private fun actualizarVisibilidadBiometrica() {

        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val bioHabilitadaConfig = sharedPref.getBoolean("biometricEnabled", true)
        val token = sharedPref.getString("apiToken", "") ?: ""

        val biometricManager = BiometricManager.from(this)
        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK

        val biometriaDisponible =
            biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

        btnBiometricLogin.visibility =
            if (biometriaDisponible && bioHabilitadaConfig && token.isNotEmpty()) View.VISIBLE else View.GONE
    }

    // ==========================================================
    // EVENTOS
    // ==========================================================

    private fun configurarEventos() {

        btnLoginTradicional.setOnClickListener {
            iniciarSesionTradicional()
        }

        btnBiometricLogin.setOnClickListener {
            biometricPrompt.authenticate(promptInfo)
        }

        tvRegistrarse.setOnClickListener {
            abrirRegistro()
        }

        ViewCompat.setOnApplyWindowInsetsListener(motionLayout) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime  = insets.getInsets(WindowInsetsCompat.Type.ime())
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

        motionLayout.post {
            actualizarVisibilidadBiometrica()
        }

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

        val usuario    = etUsuario.text.toString().trim()
        val password   = etContrasena.text.toString().trim()

        lifecycleScope.launch {
            realizarLogin(usuario, password)
        }
    }

    private suspend fun realizarLogin(usuario: String, password: String) {

        try {

            mostrarCarga()

            val peticion = LoginRequest(
                correo      = usuario,
                contrasenia = password
            )

            val response = RetrofitClient.apiService.login(peticion)

            if (response.isSuccessful && response.body()?.success == true) {

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

        biometricPrompt = BiometricPrompt(this, executor, biometricCallback)

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Inicio de sesión con huella")
            .setSubtitle("Toca el sensor de huella para ingresar")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .setNegativeButtonText("Cancelar")
            .build()
    }

    private val biometricCallback = object : BiometricPrompt.AuthenticationCallback() {

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            // No entra directo — primero verifica el token contra el servidor
            verificarTokenYEntrar()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            // FIX: usa la referencia cacheada y llama transitionToState directamente
            // sin condición de estado, evitando que la animación quede bloqueada al cancelar
            manteniendoSplash = false
            motionLayout.post {
                motionLayout.transitionToState(R.id.end)
            }
        }

        override fun onAuthenticationFailed() {
            // FIX: también libera el splash en caso de huella no reconocida
            manteniendoSplash = false
            Snackbars.info(
                findViewById(android.R.id.content),
                "Huella no reconocida",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun verificarPermisos(): Boolean{
        val permisos = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )

        val overlayGranted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                Settings.canDrawOverlays(this)
            else
                true

        val batteryGranted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager =
                    getSystemService(Context.POWER_SERVICE) as PowerManager

                powerManager.isIgnoringBatteryOptimizations(packageName)
            } else {
                true
            }

        return permisos.all {
            ContextCompat.checkSelfPermission(
                this,
                it
            ) == PackageManager.PERMISSION_GRANTED
        } && overlayGranted && batteryGranted
    }

    private fun verificarTokenYEntrar() {

        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val token  = sharedPref.getString("apiToken", "") ?: ""
        val userId = sharedPref.getInt("userId", -1)

        // Sin token → sesión expirada, debe usar contraseña
        if (token.isEmpty() || userId == -1) {
            manteniendoSplash = false
            motionLayout.transitionToState(R.id.end)   // ← usa referencia cacheada
            Snackbars.error(
                findViewById(android.R.id.content),
                "Sesión expirada. Ingresa con tu contraseña.",
                Snackbar.LENGTH_LONG
            ).show()
            return
        }

        lifecycleScope.launch {
            try {
                mostrarCarga()

                val response = RetrofitClient.apiService.getUsuario("Bearer $token", userId)

                if (response.isSuccessful) {
                    // Token vigente → puede entrar
                    irAHome()

                } else {
                    manteniendoSplash = false
                    motionLayout.transitionToState(R.id.end)   // ← usa referencia cacheada

                    // Token inválido (401 / 403) → limpiar y pedir contraseña
                    sharedPref.edit()
                        .putString("apiToken", "")
                        .putBoolean("isLoggedIn", false)
                        .apply()

                    Snackbars.error(
                        findViewById(android.R.id.content),
                        "Sesión expirada. Ingresa con tu contraseña.",
                        Snackbar.LENGTH_LONG
                    ).show()
                }

            } catch (_: Exception) {
                manteniendoSplash = false
                motionLayout.transitionToState(R.id.end)   // ← usa referencia cacheada

                Snackbars.error(
                    findViewById(android.R.id.content),
                    "Sin conexión. Intenta más tarde.",
                    Snackbar.LENGTH_LONG
                ).show()

            } finally {
                CustomDialog.dismissDialog()
            }
        }
    }

    // ==========================================================
    // VALIDACIONES
    // ==========================================================

    private fun validarEntradas(): Boolean {

        val usuarioInput   = etUsuario.text.toString().trim()
        val contrasenaInput= etContrasena.text.toString().trim()

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
            delay(1500)

            val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
            val isLoggedIn = sharedPref.getBoolean("isLoggedIn", false)

            if (isLoggedIn) {
                val bioHabilitadaConfig = sharedPref.getBoolean("biometricEnabled", true)
                val canAuthenticate = BiometricManager.from(this@MainActivity).canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
                ) == BiometricManager.BIOMETRIC_SUCCESS

                if (bioHabilitadaConfig && canAuthenticate) {
                    // Si tiene biometría habilitada, mostramos el prompt automáticamente
                    manteniendoSplash = false
                    // Dar tiempo a que el splash se quite antes de mostrar el diálogo del sistema
                    delay(500)
                    biometricPrompt.authenticate(promptInfo)
                } else {
                    // Si no, entramos directo
                    manteniendoSplash = false
                    irAHome(false)
                }
            } else {
                manteniendoSplash = false

                // FIX: usa referencia cacheada en lugar de buscar el view otra vez
                motionLayout.post {
                    if (motionLayout.currentState == R.id.start || motionLayout.currentState == -1) {
                        motionLayout.transitionToState(R.id.end)
                    }
                }
            }
        }
    }

    private fun guardarSesionExitosa(view: View, token: String = "", userId: Int = -1) {

        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)

        with(sharedPref.edit()) {
            putBoolean("isLoggedIn", true)
            if (token.isNotEmpty()) putString("apiToken", token)
            if (userId != -1)       putInt("userId", userId)
            apply()
        }

        irAHome(true)
    }

    // ==========================================================
    // NAVEGACION
    // ==========================================================

    private fun abrirRegistro() {
        startActivity(Intent(this, RegisterActivity::class.java))
    }

    private fun irAHome(mostrarBienvenida: Boolean = false) {
        if (!verificarPermisos()) {
            pedirPermisos(mostrarBienvenida)
        } else {
            lifecycleScope.launch {
                try {
                    mostrarCarga()
                    val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
                    val token = sharedPref.getString("apiToken", "") ?: ""

                    val response = RetrofitClient.casaService.getCasas("Bearer $token")
                    val tieneCasas = response.isSuccessful && !response.body()?.data.isNullOrEmpty()

                    CustomDialog.dismissDialog()

                    if (!tieneCasas) {
                        val intent = Intent(this@MainActivity, InitialSetupActivity::class.java)
                        startActivity(intent)
                    } else {
                        val intent = Intent(this@MainActivity, HomeActivity::class.java)
                        if (mostrarBienvenida) {
                            intent.putExtra("SHOW_WELCOME", true)
                        }
                        startActivity(intent)
                    }
                    finish()
                } catch (e: Exception) {
                    CustomDialog.dismissDialog()
                    val intent = Intent(this@MainActivity, HomeActivity::class.java)
                    if (mostrarBienvenida) {
                        intent.putExtra("SHOW_WELCOME", true)
                    }
                    startActivity(intent)
                    finish()
                }
            }
        }
    }

    private fun pedirPermisos(mostrarBienvenida: Boolean = false) {

        val intent = Intent(this, PermisosActivity::class.java)

        if (mostrarBienvenida) {
            intent.putExtra("SHOW_WELCOME", true)
        }

        startActivity(intent)
        finish()
    }

    // ==========================================================
    // HELPERS
    // ==========================================================

    private fun configurarSplash() {

        val splashScreen = installSplashScreen()

        splashScreen.setKeepOnScreenCondition { manteniendoSplash }

        splashScreen.setOnExitAnimationListener { splashProvider ->
            splashProvider.remove()
            // FIX: usa referencia cacheada; pero aquí motionLayout aún no está
            // inicializado (se llama antes de setContentView), así que se
            // mantiene el findViewById puntual solo en este listener
            findViewById<MotionLayout>(R.id.motionLayout)?.let { ml ->
                if (ml.currentState == R.id.start || ml.currentState == -1) {
                    ml.transitionToState(R.id.end)
                }
            }
        }
    }

    private fun configurarVentana() {

        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor      = android.graphics.Color.TRANSPARENT
        window.navigationBarColor  = android.graphics.Color.TRANSPARENT

        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars     = false
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = false
    }

    private fun intentarLoginBiometrico() {

        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)

        val bioHabilitadaConfig = sharedPref.getBoolean("biometricEnabled", true)
        if (!bioHabilitadaConfig) return

        val biometricManager = BiometricManager.from(this)

        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK

        val biometriaDisponible =
            biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

        if (!biometriaDisponible) return

        val token = sharedPref.getString("apiToken", "")
        if (!token.isNullOrEmpty()) {
            biometricPrompt.authenticate(promptInfo)
        }
    }

    private fun procesarLogout() {

        if (!intent.getBooleanExtra("FROM_LOGOUT", false)) return

        // FIX: usa referencia cacheada
        motionLayout.post {
            motionLayout.transitionToState(R.id.end)
        }

        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            db.dispositivoDao().deleteAllDispositivos()
            db.gestoDao().deleteAllGestos()
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
