package com.example.android

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class ActionsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_actions)

        val motionLayout = findViewById<androidx.constraintlayout.motion.widget.MotionLayout>(R.id.mainActions)
        motionLayout.transitionToEnd()

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        findViewById<MaterialCardView>(R.id.btnAddAction).setOnClickListener {
            startActivity(Intent(this, AddActionActivity::class.java))
        }
    }
}