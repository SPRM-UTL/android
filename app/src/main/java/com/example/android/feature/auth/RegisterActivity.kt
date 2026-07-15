package com.example.android.feature.auth

import com.example.android.R

import android.content.Context
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.android.core.network.api.ApiHandler
import com.example.android.core.network.client.RegisterRequest
import com.example.android.core.network.client.RetrofitClient
import com.example.android.core.view.CustomDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    // ==========================================================
    // VARIABLES
    // ==========================================================

    private lateinit var etNombre: TextInputEditText
    private lateinit var etCorreoReg: TextInputEditText
    private lateinit var etContrasenaReg: TextInputEditText
    private lateinit var etConfirmarReg: TextInputEditText

    private lateinit var txtInputNombre: TextInputLayout
    private lateinit var txtInputCorreoReg: TextInputLayout
    private lateinit var txtInputContrasenaReg: TextInputLayout
    private lateinit var txtInputConfirmarReg: TextInputLayout

    private lateinit var btnBackRegister: ImageView
    private lateinit var btnCrearCuenta: MaterialButton
    private lateinit var tvVolverLogin: TextView
    private lateinit var cbPrivacy: com.google.android.material.checkbox.MaterialCheckBox
    private lateinit var tvPrivacyPolicy: TextView
    
    private lateinit var mainRegister: MotionLayout

    // ==========================================================
    // LIFECYCLE
    // ==========================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = true

        setContentView(R.layout.activity_register)

        inicializar()
        configurarUI()
        configurarEventos()
    }

    // ==========================================================
    // INICIALIZACION
    // ==========================================================

    private fun inicializar() {
        inicializarVistas()
    }

    private fun inicializarVistas() {
        mainRegister = findViewById(R.id.mainRegister)
        etNombre = findViewById(R.id.etNombre)
        etCorreoReg = findViewById(R.id.etCorreoReg)
        etContrasenaReg = findViewById(R.id.etContrasenaReg)
        etConfirmarReg = findViewById(R.id.etConfirmarReg)

        txtInputNombre = findViewById(R.id.txtInputNombre)
        txtInputCorreoReg = findViewById(R.id.txtInputCorreoReg)
        txtInputContrasenaReg = findViewById(R.id.txtInputContrasenaReg)
        txtInputConfirmarReg = findViewById(R.id.txtInputConfirmarReg)

        btnBackRegister = findViewById(R.id.btnBackRegister)
        btnCrearCuenta = findViewById(R.id.btnCrearCuenta)
        tvVolverLogin = findViewById(R.id.tvVolverLogin)
        cbPrivacy = findViewById(R.id.cbPrivacy)
        tvPrivacyPolicy = findViewById(R.id.tvPrivacyPolicy)
    }

    // ==========================================================
    // CONFIGURACION UI
    // ==========================================================

    private fun configurarUI() {
        configurarInsets()
        CustomDialog.loadingDialog(this)
    }

    private fun configurarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(mainRegister) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())

            if (isKeyboardVisible) {
                // Animamos al estado compacto cuando el teclado sube
                mainRegister.transitionToEnd()
            } else {
                // Animamos al estado original cuando el teclado baja
                mainRegister.transitionToStart()
                
                // Limpiamos el foco de los inputs para que no se quede "atrapado" 
                // y permita la animación de expansión correctamente
                if (currentFocus is TextInputEditText) {
                    currentFocus?.clearFocus()
                }
            }

            // Aplicamos padding dinámico según la altura del teclado
            mainRegister.setPadding(systemBars.left, systemBars.top, systemBars.right, ime.bottom)
            insets
        }
    }

    // ==========================================================
    // EVENTOS
    // ==========================================================

    private fun configurarEventos() {
        btnBackRegister.setOnClickListener {
            finish()
        }

        tvVolverLogin.setOnClickListener {
            finish()
        }

        btnCrearCuenta.setOnClickListener {
            if (validarEntradas()) {
                llamarApiRegistro()
            }
        }

        tvPrivacyPolicy.setOnClickListener {
            mostrarAvisoPrivacidad()
        }

        // ACTIVACIÓN DE ANIMACIÓN AL TOCAR CUALQUIER INPUT
        val inputs = listOf(etNombre, etCorreoReg, etContrasenaReg, etConfirmarReg)
        inputs.forEach { input ->
            input.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    mainRegister.transitionToEnd()
                }
            }
            input.setOnClickListener {
                mainRegister.transitionToEnd()
            }
            
            // Al presionar Enter (Done/Next)
            input.setOnEditorActionListener { v, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || 
                    v.id == R.id.etConfirmarReg) {
                    v.clearFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                    mainRegister.transitionToStart()
                }
                false
            }
        }

        // TOCAR FUERA para cerrar teclado y resetear pantalla
        val cerrarTecladoAction = View.OnClickListener {
            val currentFocus = currentFocus
            if (currentFocus is TextInputEditText) {
                currentFocus.clearFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(currentFocus.windowToken, 0)
            }
            // Forzar el regreso de la animación si no se disparó por insets
            mainRegister.transitionToStart()
        }

        mainRegister.setOnClickListener(cerrarTecladoAction)
        findViewById<View>(R.id.scrollForm).setOnClickListener(cerrarTecladoAction)
        findViewById<View>(R.id.containerForm).setOnClickListener(cerrarTecladoAction)
    }

    // ==========================================================
    // LÓGICA DE NEGOCIO
    // ==========================================================

    private fun validarEntradas(): Boolean {
        val nombre = etNombre.text.toString().trim()
        val correo = etCorreoReg.text.toString().trim()
        val contrasena = etContrasenaReg.text.toString().trim()
        val confirmar = etConfirmarReg.text.toString().trim()

        var isValid = true

        if (nombre.isEmpty()) {
            txtInputNombre.error = "El nombre es obligatorio"
            isValid = false
        } else {
            txtInputNombre.error = null
        }

        if (correo.isEmpty()) {
            txtInputCorreoReg.error = "El correo es obligatorio"
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            txtInputCorreoReg.error = "Ingresa un correo válido"
            isValid = false
        } else {
            txtInputCorreoReg.error = null
        }

        val passwordRegex = Regex("""^(?=.*[A-Z])(?=.*[@$!%*?&._-])[A-Za-z\d@$!%*?&._-]{8,}$""")

        if (contrasena.isEmpty()) {
            txtInputContrasenaReg.error = "La contraseña es obligatoria"
            isValid = false
        } else if (!contrasena.matches(passwordRegex)) {
            txtInputContrasenaReg.error = "Mín. 8 caracteres, 1 mayúscula y 1 símbolo"
            isValid = false
        } else {
            txtInputContrasenaReg.error = null
        }

        if (contrasena != confirmar) {
            txtInputConfirmarReg.error = "Las contraseñas no coinciden"
            isValid = false
        } else {
            txtInputConfirmarReg.error = null
        }

        if (!cbPrivacy.isChecked) {
            android.widget.Toast.makeText(this, "Debes aceptar el aviso de privacidad", android.widget.Toast.LENGTH_SHORT).show()
            isValid = false
        }

        return isValid
    }

    private fun mostrarAvisoPrivacidad() {
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        
        val scrollLayout = android.widget.ScrollView(this)
        val padding = (24 * resources.displayMetrics.density).toInt()
        scrollLayout.setPadding(padding, padding, padding, padding)

        val textLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }

        val title = TextView(this).apply {
            text = "Aviso de Privacidad"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#1a1a1a"))
            setPadding(0, 0, 0, (16 * resources.displayMetrics.density).toInt())
        }

        val body = TextView(this).apply {
            text = "En SPRM, respetamos tu privacidad y protegemos tus datos personales.\n\n" +
                   "1. Información que recopilamos: La información que proporcionas (nombre y correo electrónico) será utilizada para crear y gestionar tu cuenta.\n\n" +
                   "2. Uso de la información: Usaremos estos datos únicamente para brindarte acceso a las funcionalidades de control de dispositivos inteligentes dentro de tu hogar y aplicación.\n\n" +
                   "3. Protección: Tus contraseñas viajan de manera encriptada y no compartiremos tus datos con terceros sin tu consentimiento explícito.\n\n" +
                   "4. Permisos locales: La aplicación requiere acceso a Bluetooth, Wi-Fi y Ubicación de manera temporal con el fin único de vincular dispositivos cercanos."
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#757575"))
            setLineSpacing(4f, 1f)
        }

        textLayout.addView(title)
        textLayout.addView(body)
        scrollLayout.addView(textLayout)

        bottomSheet.setContentView(scrollLayout)
        bottomSheet.show()
    }

    private fun llamarApiRegistro() {
        val request = RegisterRequest(
            nombre = etNombre.text.toString().trim(),
            correo = etCorreoReg.text.toString().trim(),
            contrasenia = etContrasenaReg.text.toString().trim()
        )

        btnCrearCuenta.isEnabled = false

        lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = this@RegisterActivity,
                showLoading = true,
                loadingTitle = "Registrando",
                loadingMessage = "Creando tu cuenta, por favor espera...",
                apiCall = {
                    RetrofitClient.apiService.registrar(request)
                },
                onSuccess = { response ->
                    val mensajeServidor = response.data.mensaje
                    
                    CustomDialog.showSuccessDialog(
                        titleDialog = "¡Registro Exitoso!",
                        subtitleDialog = mensajeServidor
                    ) {
                        finish()
                    }
                },
                onError = { errorMsg ->
                    CustomDialog.showErrorDialog(
                        titleDialog = "Error de registro",
                        subtitleDialog = errorMsg,
                        retryAction = { llamarApiRegistro() },
                        backAction = { btnCrearCuenta.isEnabled = true }
                    )
                }
            )
        }
    }
}
