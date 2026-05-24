package com.example.android

import android.content.Context
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.android.network.RetrofitClient
import com.example.android.network.UpdateUserRequest
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

    private var userIdGuardado: Int = -1
    private var tokenGuardado: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile)

        val mainProfile = findViewById<View>(R.id.mainProfile)
        ViewCompat.setOnApplyWindowInsetsListener(mainProfile) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        etNombrePerfil = findViewById(R.id.etNombrePerfil)
        txtInputNombrePerfil = findViewById(R.id.txtInputNombrePerfil)
        etCorreoPerfil = findViewById(R.id.etCorreoPerfil)
        etContrasenaPerfil = findViewById(R.id.etContrasenaPerfil)
        etConfirmarContrasena = findViewById(R.id.etConfirmarContrasena)

        txtInputCorreo = findViewById(R.id.txtInputCorreo)
        txtInputContrasena = findViewById(R.id.txtInputContrasena)
        txtInputConfirmar = findViewById(R.id.txtInputConfirmar)

        btnGuardarPerfil = findViewById(R.id.btnGuardarPerfil)
        val btnBack = findViewById<ImageView>(R.id.btnBack)

        btnBack.setOnClickListener { finish() }

        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        tokenGuardado = sharedPref.getString("apiToken", "") ?: ""
        userIdGuardado = sharedPref.getInt("userId", -1)

        if (userIdGuardado != -1 && tokenGuardado.isNotEmpty()) {
            cargarDatosAPI()
        } else {
            Snackbar.make(mainProfile, "Error de sesión local", Snackbar.LENGTH_SHORT).show()
        }

        btnGuardarPerfil.setOnClickListener { view ->
            if (validarEntradas()) {
                actualizarDatosAPI(view)
            }
        }
    }

    private fun validarEntradas(): Boolean {
        val correoInput = etCorreoPerfil.text.toString().trim()
        val contrasenaInput = etContrasenaPerfil.text.toString().trim()
        val confirmarInput = etConfirmarContrasena.text.toString().trim()
        var isValid = true

        if (correoInput.isEmpty()) {
            txtInputCorreo.error = "El correo no puede estar vacío"
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(correoInput).matches()) {
            txtInputCorreo.error = "Ingresa un formato de correo electrónico válido"
            isValid = false
        } else {
            txtInputCorreo.error = null
        }

        val passwordRegex = Regex("""^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&._-])[A-Za-z\d@$!%*?&._-]{8,}$""")

        if (contrasenaInput.isNotEmpty()) {
            if (!contrasenaInput.matches(passwordRegex)) {
                txtInputContrasena.error = "Mín. 8 caracteres, 1 mayúscula, 1 minúscula, 1 número y 1 símbolo (@$!%*?&._-)"
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
            try {
                val response = RetrofitClient.apiService.getUsuario(tokenGuardado, userIdGuardado)

                if (response.isSuccessful && response.body() != null) {
                    val datosUsuario = response.body()!!.data

                    etNombrePerfil.setText(datosUsuario.nombre)
                    etCorreoPerfil.setText(datosUsuario.correo)

                    btnGuardarPerfil.isEnabled = true
                } else {
                    Snackbar.make(findViewById(android.R.id.content), "No se pudieron cargar tus datos", Snackbar.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Snackbar.make(findViewById(android.R.id.content), "Error de red: ${e.message}", Snackbar.LENGTH_LONG).show()
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
            try {
                val response = RetrofitClient.apiService.updateUsuario(tokenGuardado, userIdGuardado, request)

                if (response.isSuccessful) {
                    Snackbar.make(view, "Perfil actualizado correctamente", Snackbar.LENGTH_SHORT).show()
                    delay(1200)
                    finish()
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Error al actualizar"
                    Snackbar.make(view, errorMsg, Snackbar.LENGTH_LONG).show()
                    btnGuardarPerfil.isEnabled = true
                }
            } catch (e: Exception) {
                Snackbar.make(view, "Error de red: ${e.message}", Snackbar.LENGTH_LONG).show()
                btnGuardarPerfil.isEnabled = true
            }
        }
    }
}