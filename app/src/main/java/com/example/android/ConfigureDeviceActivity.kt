package com.example.android

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ConfigureDeviceActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_configure_device)
        
        val motionLayout = findViewById<androidx.constraintlayout.motion.widget.MotionLayout>(R.id.mainConfigureDevice)
        motionLayout.transitionToEnd()

        val mainView = findViewById<android.view.View>(R.id.iconContainer).parent as android.view.View
        ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        mainView.setOnClickListener {
            val intent = android.content.Intent(this, ActionsActivity::class.java)
            startActivity(intent)
        }
    }
}