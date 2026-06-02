package com.example.android

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.android.network.RegisterRequest
import com.example.android.network.RetrofitClient
import com.example.android.view.CustomDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var etNombre: TextInputEditText
    private lateinit var etCorreoReg: TextInputEditText
    private lateinit var etContrasenaReg: TextInputEditText
    private lateinit var etConfirmarReg: TextInputEditText

    private lateinit var txtInputNombre: TextInputLayout
    private lateinit var txtInputCorreoReg: TextInputLayout
    private lateinit var txtInputContrasenaReg: TextInputLayout
    private lateinit var txtInputConfirmarReg: TextInputLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)

        val mainRegister = findViewById<View>(R.id.mainRegister)
        ViewCompat.setOnApplyWindowInsetsListener(mainRegister) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        etNombre = findViewById(R.id.etNombre)
        etCorreoReg = findViewById(R.id.etCorreoReg)
        etContrasenaReg = findViewById(R.id.etContrasenaReg)
        etConfirmarReg = findViewById(R.id.etConfirmarReg)

        txtInputNombre = findViewById(R.id.txtInputNombre)
        txtInputCorreoReg = findViewById(R.id.txtInputCorreoReg)
        txtInputContrasenaReg = findViewById(R.id.txtInputContrasenaReg)
        txtInputConfirmarReg = findViewById(R.id.txtInputConfirmarReg)

        CustomDialog.loadingDialog(this)

        val btnBackRegister = findViewById<ImageView>(R.id.btnBackRegister)
        val btnCrearCuenta = findViewById<MaterialButton>(R.id.btnCrearCuenta)
        val tvVolverLogin = findViewById<android.widget.TextView>(R.id.tvVolverLogin)

        btnBackRegister.setOnClickListener {
            finish()
        }

        tvVolverLogin.setOnClickListener {
            finish()
        }

        btnCrearCuenta.setOnClickListener { view ->
            if (validarEntradas()) {
                llamarApiRegistro(view)
            }
        }
    }

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

        val passwordRegex = Regex("""^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&._-])[A-Za-z\d@$!%*?&._-]{8,}$""")

        if (contrasena.isEmpty()) {
            txtInputContrasenaReg.error = "La contraseña es obligatoria"
            isValid = false
        } else if (!contrasena.matches(passwordRegex)) {
            txtInputContrasenaReg.error = "Mínimo 8 caracteres, 1 mayúscula, 1 minúscula, 1 número y 1 símbolo (@$!%*?&._-)"
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

        return isValid
    }

    private fun llamarApiRegistro(view: View) {
        val request = RegisterRequest(
            nombre = etNombre.text.toString().trim(),
            correo = etCorreoReg.text.toString().trim(),
            contrasenia = etContrasenaReg.text.toString().trim()
        )

        val btnCrearCuenta = view as MaterialButton
        btnCrearCuenta.isEnabled = false

        CustomDialog.showDialog(
            titleDialog = "Registrando",
            subtitleDialog = "Creando tu cuenta, por favor espera...",
            type = CustomDialog.DialogType.LOADING
        )

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.registrar(request)

                if (response.isSuccessful && response.body() != null) {
                    val mensajeServidor = response.body()!!.data.mensaje
                    
                    CustomDialog.showSuccessDialog(
                        titleDialog = "¡Registro Exitoso!",
                        subtitleDialog = mensajeServidor
                    ) {
                        finish()
                    }
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Error al registrar"
                    CustomDialog.showErrorDialog(
                        titleDialog = "Error de registro",
                        subtitleDialog = errorMsg,
                        retryAction = { llamarApiRegistro(view) },
                        backAction = { btnCrearCuenta.isEnabled = true }
                    )
                }
            } catch (e: Exception) {
                CustomDialog.showErrorDialog(
                    titleDialog = "Error de conexión",
                    subtitleDialog = "No se pudo conectar con el servidor: ${e.message}",
                    retryAction = { llamarApiRegistro(view) },
                    backAction = { btnCrearCuenta.isEnabled = true }
                )
            }
        }
    }
}