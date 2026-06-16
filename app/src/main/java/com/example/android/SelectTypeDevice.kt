package com.example.android

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SelectTypeDevice : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_select_type_device)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.header)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        // Sin FILTRO_TIPO → AddDeviceActivity recibe null → muestra todos los dispositivos
        findViewById<View>(R.id.itemAsistente).setOnClickListener {
            startActivity(Intent(this, AddDeviceActivity::class.java))
        }

        findViewById<View>(R.id.itemBocina).setOnClickListener {
            val intent = Intent(this, AddDeviceActivity::class.java).apply {
                putExtra("FILTRO_TIPO", "Bocinas")
            }
            startActivity(intent)
        }

        findViewById<View>(R.id.itemAudifonos).setOnClickListener {
            val intent = Intent(this, AddDeviceActivity::class.java).apply {
                putExtra("FILTRO_TIPO", "Audífonos")
            }
            startActivity(intent)
        }
    }
}