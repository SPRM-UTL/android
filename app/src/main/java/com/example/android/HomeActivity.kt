package com.example.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.android.network.RetrofitClient
import kotlinx.coroutines.launch
import kotlin.jvm.java

class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val btnCapturarGesto = findViewById<Button>(R.id.btnCapturarGesto)
        btnCapturarGesto.setOnClickListener {
            startActivity(Intent(this, GestureActivity::class.java))
        }

        val btnLogout = findViewById<Button>(R.id.btnLogout)

        btnLogout.setOnClickListener {
            btnLogout.setOnClickListener {
                val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
                val tokenGuardado = sharedPref.getString("apiToken", "") ?: ""

                if (tokenGuardado.isEmpty()) {
                    Toast.makeText(
                        this,
                        "Error: No hay un token guardado localmente",
                        Toast.LENGTH_LONG
                    ).show()
                }

                lifecycleScope.launch {
                    try {
                        val response = RetrofitClient.apiService.logout(tokenGuardado)

                        if (response.isSuccessful) {
                            Toast.makeText(
                                this@HomeActivity,
                                "Sesión cerrada",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            val codigoError = response.code()
                            val cuerpoError = response.errorBody()?.string() ?: ""
                            Toast.makeText(
                                this@HomeActivity,
                                "Error API $codigoError: $cuerpoError",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                    } catch (e: Exception) {
                        Toast.makeText(
                            this@HomeActivity,
                            "Error de conexión: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    with(sharedPref.edit()) {
                        putBoolean("isLoggedIn", false)
                        putString("apiToken", "")
                        apply()
                    }

                    val intent = Intent(this@HomeActivity, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
        }
    }
}