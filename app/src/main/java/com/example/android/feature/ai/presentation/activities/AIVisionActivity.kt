package com.example.android.feature.ai.presentation.activities
import com.example.android.feature.ai.presentation.activities.AIVisionActivity
import com.example.android.feature.ai.domain.models.GestureAnalyzerConfig
import com.example.android.feature.ai.presentation.fragments.AIConfigFragment
import com.example.android.feature.ai.presentation.fragments.AIMonitorFragment
import com.example.android.feature.gesture.GestosFragment

import android.os.Bundle
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.android.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator


class AIVisionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Aplicar Safe Area y estilo Edge-to-Edge
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightNavigationBars = true
            isAppearanceLightStatusBars = false // statusBar oscura porque el header es Teal
        }
        
        setContentView(R.layout.activity_ai_vision)

        // Manejo de Insets (Safe Area)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainAIVision)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            
            // Añadir padding abajo para Navigation Bar
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom + ime.bottom)
            
            // Añadir padding arriba para Status Bar en el AppBarLayout
            val appBar = findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.appBarLayout)
            appBar?.setPadding(0, systemBars.top, 0, 0)
            
            insets
        }

        // Configurar botón volver del nuevo header
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        val viewPager = findViewById<ViewPager2>(R.id.viewPagerAIVision)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayoutAIVision)

        val adapter = AIVisionPagerAdapter(this)
        viewPager.adapter = adapter
        viewPager.offscreenPageLimit = 2 // Mantener algunas pestañas en memoria

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Monitor"
                1 -> "Config"
                else -> "Tab $position"
            }
        }.attach()

        // Si se nos pidió abrir un tab específico (ej: desde GestosFragment)
        val openTab = intent.getIntExtra("OPEN_TAB", 0)
        if (openTab in 0..1) {
            viewPager.currentItem = openTab
        }

        // Cargar configuración de gestos en memoria para el analizador
        loadGestureConfig()
    }

    private fun loadGestureConfig() {
        val sharedPref = getSharedPreferences("ai_gestures_config", android.content.Context.MODE_PRIVATE)
        val allEntries = sharedPref.all
        val activeGestures = mutableMapOf<String, Boolean>()
        for ((key, value) in allEntries) {
            if (value is Boolean) {
                activeGestures[key] = value
            }
        }
        GestureAnalyzerConfig.updateConfig(activeGestures)
    }

    private inner class AIVisionPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> AIMonitorFragment()
                1 -> AIConfigFragment()
                else -> AIMonitorFragment()
            }
        }
    }
}
