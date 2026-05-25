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
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
class ProfileActivity : AppCompatActivity() {

    private lateinit var etCorreoPerfil: TextInputEditText
    private lateinit var etContrasenaPerfil: TextInputEditText
    private lateinit var etConfirmarContrasena: TextInputEditText

    private lateinit var txtInputCorreo: TextInputLayout
    private lateinit var txtInputContrasena: TextInputLayout
    private lateinit var txtInputConfirmar: TextInputLayout

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

        etCorreoPerfil = findViewById(R.id.etCorreoPerfil)
        etContrasenaPerfil = findViewById(R.id.etContrasenaPerfil)
        etConfirmarContrasena = findViewById(R.id.etConfirmarContrasena)

        txtInputCorreo = findViewById(R.id.txtInputCorreo)
        txtInputContrasena = findViewById(R.id.txtInputContrasena)
        txtInputConfirmar = findViewById(R.id.txtInputConfirmar)

        val btnGuardarPerfil = findViewById<MaterialButton>(R.id.btnGuardarPerfil)
        val btnBack = findViewById<ImageView>(R.id.btnBack)

        btnBack.setOnClickListener {
            finish()
        }

        cargarDatosLocales()

        btnGuardarPerfil.setOnClickListener {
            if (validarEntradas()) {
                guardarDatosSimulados(it)
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

        if (contrasenaInput.isNotEmpty()) {
            if (contrasenaInput.length < 6) {
                txtInputContrasena.error = "La contraseña debe tener al menos 6 caracteres"
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

    private fun cargarDatosLocales() {
        etCorreoPerfil.setText("prueba@gmail.com")
    }

    private fun guardarDatosSimulados(view: View) {
        val correoLimpio = etCorreoPerfil.text.toString().trim()
        val contrasenaLimpia = etContrasenaPerfil.text.toString().trim()

        // Aquí ira la lógica de la API para datos reales

        Snackbars.info(view, "Datos guardados", Snackbar.LENGTH_SHORT).show()

        lifecycleScope.launch {
            delay(1200)
            finish()
        }
    }
}