package com.example.android.feature.home

import com.example.android.R

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import android.view.View
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.android.core.ui.components.BottomBarWithFab
import com.example.android.feature.gesture.GestosFragment
import com.example.android.core.ui.dialogs.MenuBottomSheetDialog

class HomeActivity : AppCompatActivity() {

    private var currentScreenState by mutableStateOf("home")
    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = true

        setContentView(R.layout.activity_home)

        viewPager = findViewById(R.id.viewPager)
        viewPager.adapter = HomePagerAdapter(this)
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentScreenState = if (position == 0) "home" else "gestos"
            }
        })

        configurarInsets()
        configurarBottomBar()
    }

    private fun configurarInsets() {
        val rootView = findViewById<View>(R.id.mainHomeActivity)
        val bottomBarContainer = findViewById<FrameLayout>(R.id.bottom_bar_container)
        val viewPagerView = findViewById<View>(R.id.viewPager)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            bottomBarContainer.setPadding(0, 0, 0, systemBars.bottom)
            ViewCompat.dispatchApplyWindowInsets(viewPagerView, insets)

            insets
        }
    }

    private fun configurarBottomBar() {
        val container = findViewById<FrameLayout>(R.id.bottom_bar_container)
        val composeView = ComposeView(this).apply {
            setContent {
                var isMenuOpen by androidx.compose.runtime.remember { mutableStateOf(false) }

                BottomBarWithFab(
                    currentScreen = currentScreenState,
                    isMenuOpen = isMenuOpen,
                    onHomeClick = {
                        if (currentScreenState != "home") {
                            viewPager.currentItem = 0
                        }
                    },
                    onGesturesClick = {
                        if (currentScreenState != "gestos") {
                            viewPager.currentItem = 1
                        }
                    },
                    onFabClick = {
                        isMenuOpen = true
                        abrirMenuPrincipal(onDismiss = { isMenuOpen = false })
                    }
                )
            }
        }
        container.addView(composeView)
    }

    private fun abrirMenuPrincipal(onDismiss: () -> Unit = {}) {
        if (supportFragmentManager.findFragmentByTag("MenuBottomSheet") != null) return
        val sheet = MenuBottomSheetDialog(this)
        sheet.onDismissCallback = onDismiss
        sheet.show(supportFragmentManager, "MenuBottomSheet")
    }

    private inner class HomePagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return if (position == 0) HomeFragment() else GestosFragment()
        }
    }
}