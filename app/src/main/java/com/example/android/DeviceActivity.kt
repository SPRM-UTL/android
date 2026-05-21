package com.example.android

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class DeviceActivity : AppCompatActivity(){

    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_device)

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val contentLayout = findViewById<View>(R.id.contentLayout)
        val searchingState = findViewById<View>(R.id.searchingState)

        val itemAsistente = findViewById<View>(R.id.iconContainer1).parent as View

        btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        itemAsistente.setOnClickListener {
            contentLayout.visibility = View.GONE
            searchingState.visibility = View.VISIBLE

            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent(this, ConfigureDeviceActivity::class.java)
                startActivity(intent)

                searchingState.visibility = View.GONE
                contentLayout.visibility = View.VISIBLE
            }, 3000)
        }
    }
}