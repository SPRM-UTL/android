package com.example.android

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
import android.content.Context
import android.content.Intent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.android.ui.components.BottomBarWithFab

class HomeActivity : AppCompatActivity() {

    private var currentScreenState by mutableStateOf("home")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = true

        setContentView(R.layout.activity_home)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, HomeFragment())
                .commit()
        }

        configurarInsets()
        configurarBottomBar()
    }

    override fun onResume() {
        super.onResume()
        verificarControladorConfigurado()
    }

    private var skipCameraConfig = false

    private fun verificarControladorConfigurado() {
        if (skipCameraConfig) return
        val prefs = getSharedPreferences("EspConfigPrefs", Context.MODE_PRIVATE)
        val mac = prefs.getString("saved_mac_address", null)
        if (mac.isNullOrEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Cámara no configurada")
                .setMessage("Es recomendable configurar la cámara que controlará tus dispositivos. Puedes hacerlo ahora o más tarde.")
                .setCancelable(false)
                .setPositiveButton("Configurar ahora") { _, _ ->
                    startActivity(Intent(this, EspConfigActivity::class.java))
                }
                .setNegativeButton("Continuar sin cámara") { dialog, _ ->
                    skipCameraConfig = true
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun configurarInsets() {
        val rootView = findViewById<View>(R.id.mainHomeActivity)
        val bottomBarContainer = findViewById<FrameLayout>(R.id.bottom_bar_container)
        val fragmentContainer = findViewById<View>(R.id.fragmentContainer)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Padding a la barra inferior para que suba según la Navigation Bar del sistema
            bottomBarContainer.setPadding(0, 0, 0, systemBars.bottom)

            // Pasar los insets al contenedor de fragmentos para que la parte superior respete el Status Bar
            ViewCompat.dispatchApplyWindowInsets(fragmentContainer, insets)
            
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
                            currentScreenState = "home"
                            switchFragment(HomeFragment())
                        }
                    },
                    onGesturesClick = {
                        if (currentScreenState != "gestos") {
                            currentScreenState = "gestos"
                            switchFragment(GestosFragment())
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

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun abrirMenuPrincipal(onDismiss: () -> Unit = {}) {
        if (supportFragmentManager.findFragmentByTag("MenuBottomSheet") != null) return
        val sheet = MenuBottomSheetDialog(this)
        sheet.onDismissCallback = onDismiss
        sheet.show(supportFragmentManager, "MenuBottomSheet")
    }
}
