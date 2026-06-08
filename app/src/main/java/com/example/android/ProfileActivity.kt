package com.example.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.android.network.RetrofitClient
import com.example.android.network.UpdateUserRequest
import com.example.android.view.CustomDialog
import com.example.android.view.Snackbars
import com.example.android.view.cambiarColorStatusBar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {

    private lateinit var etNombrePerfil: TextInputEditText
    private lateinit var txtInputNombrePerfil: TextInputLayout
    private lateinit var etCorreoPerfil: TextInputEditText
    private lateinit var etContrasenaPerfil: TextInputEditText
    private lateinit var etConfirmarContrasena: TextInputEditText

    private lateinit var txtInputCorreo: TextInputLayout
    private lateinit var txtInputContrasena: TextInputLayout
    private lateinit var txtInputConfirmar: TextInputLayout
    private lateinit var btnGuardarPerfil: MaterialButton
    private lateinit var tvHola: TextView

    private lateinit var vistaRaiz : View
    private lateinit var btnLogout : Button
    private lateinit var mainProfile: MotionLayout

    private var userIdGuardado: Int = -1
    private var tokenGuardado: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = true

        setContentView(R.layout.activity_profile)

        cambiarColorStatusBar(R.color.teal_primary, true)
        mainProfile = findViewById(R.id.mainProfile)
        
        ViewCompat.setOnApplyWindowInsetsListener(mainProfile) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())

            if (isKeyboardVisible) {
                mainProfile.transitionToEnd()
            } else {
                mainProfile.transitionToStart()
                if (currentFocus is TextInputEditText) {
                    currentFocus?.clearFocus()
                }
            }
            
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, ime.bottom)
            insets
        }

        inicializarVistas()
        cargarIconosPerfil()

        CustomDialog.loadingDialog(this)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        tokenGuardado = sharedPref.getString("apiToken", "") ?: ""
        userIdGuardado = sharedPref.getInt("userId", -1)

        if (userIdGuardado != -1 && tokenGuardado.isNotEmpty()) {
            cargarDatosAPI()
        } else {
            Snackbar.make(mainProfile, "Error de sesión local", Snackbar.LENGTH_SHORT).show()
        }

        cargarBoton()
    }

    private fun inicializarVistas() {
        vistaRaiz = findViewById(android.R.id.content)
        etNombrePerfil = findViewById(R.id.etNombrePerfil)
        txtInputNombrePerfil = findViewById(R.id.txtInputNombrePerfil)
        etCorreoPerfil = findViewById(R.id.etCorreoPerfil)
        etContrasenaPerfil = findViewById(R.id.etContrasenaPerfil)
        etConfirmarContrasena = findViewById(R.id.etConfirmarContrasena)

        txtInputCorreo = findViewById(R.id.txtInputCorreo)
        txtInputContrasena = findViewById(R.id.txtInputContrasena)
        txtInputConfirmar = findViewById(R.id.txtInputConfirmar)

        btnGuardarPerfil = findViewById(R.id.btnGuardarPerfil)
        tvHola = findViewById(R.id.tvHola)

        // ACTIVACIÓN DE ANIMACIÓN AL TOCAR CUALQUIER INPUT
        val inputs = listOf(etNombrePerfil, etCorreoPerfil, etContrasenaPerfil, etConfirmarContrasena)
        inputs.forEach { input ->
            input.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) mainProfile.transitionToEnd()
            }
            input.setOnClickListener { mainProfile.transitionToEnd() }

            // Al presionar Enter (Done/Next)
            input.setOnEditorActionListener { v, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || 
                    v.id == R.id.etConfirmarContrasena) {
                    v.clearFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                    mainProfile.transitionToStart()
                }
                false
            }
        }
        
        // TOCAR FUERA para cerrar teclado y resetear pantalla
        val cerrarTecladoAction = View.OnClickListener {
            val focus = currentFocus
            if (focus is TextInputEditText) {
                focus.clearFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(focus.windowToken, 0)
            }
            mainProfile.transitionToStart()
        }

        mainProfile.setOnClickListener(cerrarTecladoAction)
        findViewById<View>(R.id.scrollProfile).setOnClickListener(cerrarTecladoAction)
        findViewById<View>(R.id.containerFormProfile).setOnClickListener(cerrarTecladoAction)
    }

    private fun cargarIconosPerfil() {
        findViewById<ImageButton>(R.id.btnBack)?.let {
            it.setImageResource(R.drawable.arrow_left)
        }

        findViewById<MaterialButton>(R.id.btnGuardarPerfil)?.let { botonGuardar ->
            // Se puede cargar icono local si se requiere
        }

        findViewById<MaterialButton>(R.id.logout)?.let { botonLogout ->
            // Se puede cargar icono local si se requiere
        }
    }

    private fun cargarBoton() {
        btnGuardarPerfil.setOnClickListener { view ->
            if (validarEntradas()) {
                actualizarDatosAPI(view)
            }
        }
    }

    private fun validarEntradas(): Boolean {
        val nombreInput = etNombrePerfil.text.toString().trim()
        val correoInput = etCorreoPerfil.text.toString().trim()
        val contrasenaInput = etContrasenaPerfil.text.toString().trim()
        val confirmarInput = etConfirmarContrasena.text.toString().trim()
        var isValid = true

        if (nombreInput.isEmpty()) {
            txtInputNombrePerfil.error = "El nombre no puede estar vacío"
            isValid = false
        } else {
            txtInputNombrePerfil.error = null
        }

        if (correoInput.isEmpty()) {
            txtInputCorreo.error = "El correo no puede estar vacío"
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(correoInput).matches()) {
            txtInputCorreo.error = "Ingresa un formato de correo electrónico válido"
            isValid = false
        } else {
            txtInputCorreo.error = null
        }

        val passwordRegex = Regex("""^(?=.*[A-Z])(?=.*[@$!%*?&._-])[A-Za-z\d@$!%*?&._-]{8,}$""")

        if (contrasenaInput.isNotEmpty()) {
            if (!contrasenaInput.matches(passwordRegex)) {
                txtInputContrasena.error = "Mín. 8 caracteres, 1 mayúscula y 1 símbolo"
                isValid = false
            } else {
                txtInputContrasena.error = null
            }

            if (contrasenaInput != confirmarInput) {
                txtInputConfirmar.error = "Las contraseñas no coinciden"
                isValid = false
            } else {
                txtInputConfirmar.error = null
            }
        } else {
            txtInputContrasena.error = null
            txtInputConfirmar.error = null

            if (confirmarInput.isNotEmpty()) {
                txtInputConfirmar.error = "Debes ingresar la nueva contraseña arriba"
                isValid = false
            }
        }

        return isValid
    }

    private fun cargarDatosAPI() {
        btnGuardarPerfil.isEnabled = false

        lifecycleScope.launch {
            CustomDialog.showDialog("Cargando", "Obteniendo datos del perfil...", CustomDialog.DialogType.LOADING)
            try {
                val response = RetrofitClient.apiService.getUsuario(tokenGuardado, userIdGuardado)

                if (response.isSuccessful && response.body() != null) {
                    val datosUsuario = response.body()!!.data

                    etNombrePerfil.setText(datosUsuario.nombre)
                    etCorreoPerfil.setText(datosUsuario.correo)

                    val primerNombre = datosUsuario.nombre?.split(" ")?.firstOrNull() ?: ""
                    tvHola.text = "¡Hola, $primerNombre! 👋"

                    btnGuardarPerfil.isEnabled = true
                    CustomDialog.dismissDialog()
                }else if (response.code() == 401) {

                    CustomDialog.showErrorDialog(
                        titleDialog = "Sesión expirada",
                        subtitleDialog = "Tu sesión ha expirado. Inicia sesión nuevamente.",
                        positiveText = "Ir al Login",
                        negativeText = "Cerrar",
                        retryAction = {
                            val sharedPref = getSharedPreferences(
                                "SesionApp",
                                Context.MODE_PRIVATE
                            )

                            sharedPref.edit()
                                .clear()
                                .apply()

                            val intent = Intent(
                                this@ProfileActivity,
                                MainActivity::class.java
                            ).apply {
                                putExtra("FROM_LOGOUT", true)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }

                            startActivity(intent)
                            finish()
                        },
                        backAction = { finish() }
                    )
                }
                else {
                    CustomDialog.showErrorDialog(
                        "Error al cargar",
                        "No se pudieron cargar tus datos desde el servidor.",
                        retryAction = { cargarDatosAPI() },
                        backAction = { finish() }
                    )
                }
            } catch (e: Exception) {
                CustomDialog.showErrorDialog(
                    "Error de red",
                    "No fue posible conectar con el servidor. Inténtalo de nuevo.",
                    retryAction = { cargarDatosAPI() },
                    backAction = { finish() }
                )
            }
        }
    }

    private fun actualizarDatosAPI(view: View) {
        val nombreLimpio = etNombrePerfil.text.toString().trim()
        val correoLimpio = etCorreoPerfil.text.toString().trim()
        val contrasenaLimpia = etContrasenaPerfil.text.toString().trim()

        val request = UpdateUserRequest(
            id = userIdGuardado,
            nombre = nombreLimpio,
            correo = correoLimpio,
            contrasenia = if (contrasenaLimpia.isEmpty()) "" else contrasenaLimpia
        )

        btnGuardarPerfil.isEnabled = false

        lifecycleScope.launch {
            CustomDialog.showDialog("Guardando", "Actualizando tu información...", CustomDialog.DialogType.LOADING)
            
            val startTime = System.currentTimeMillis()
            var success = false
            var errorTitle = ""
            var errorMessage = ""

            try {
                val response = RetrofitClient.apiService.updateUsuario(tokenGuardado, userIdGuardado, request)

                if (response.isSuccessful) {
                    success = true
                } else {
                    errorTitle = "Error al actualizar"
                    errorMessage = response.errorBody()?.string() ?: "Hubo un problema al guardar los cambios."
                }
            } catch (e: Exception) {
                errorTitle = "Error de red"
                errorMessage = "No se pudo conectar con el servidor. Inténtalo de nuevo más tarde."
                android.util.Log.e("ProfileActivity", "Error al actualizar perfil", e)
            }

            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime < 1000) {
                delay(1000 - elapsedTime)
            }

            if (success) {
                CustomDialog.dismissDialog()
                Snackbars.success(view, "Perfil actualizado correctamente", Snackbar.LENGTH_SHORT).show()
                delay(1200)
                finish()
            } else {
                CustomDialog.showErrorDialog(
                    titleDialog = errorTitle,
                    subtitleDialog = errorMessage,
                    retryAction = { actualizarDatosAPI(view) },
                    backAction = { finish() }
                )
                btnGuardarPerfil.isEnabled = true
            }
        }
    }

    override fun onDestroy() {
        CustomDialog.dismissDialog()
        super.onDestroy()
    }
}
