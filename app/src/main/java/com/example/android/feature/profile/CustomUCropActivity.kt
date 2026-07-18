package com.example.android.feature.profile

import com.example.android.R

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import com.yalantis.ucrop.UCropActivity

class CustomUCropActivity : UCropActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Aplicar edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window?.let { win ->
            win.statusBarColor = ContextCompat.getColor(this, R.color.teal_primary)
            win.navigationBarColor = android.graphics.Color.TRANSPARENT
        }

        val rootView = findViewById<View>(android.R.id.content)
        
        // Iconos de status bar en blanco
        WindowInsetsControllerCompat(window, rootView).isAppearanceLightStatusBars = false
        
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Ajustar padding
            v.setPadding(
                0,
                systemBars.top,
                0,
                systemBars.bottom
            )
            
            insets
        }
    }
}
