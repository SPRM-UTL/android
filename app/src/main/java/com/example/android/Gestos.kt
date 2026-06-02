package com.example.android

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.android.ui.components.BottomBarWithFab
import com.example.android.view.Snackbars
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial


class Gestos : AppCompatActivity() {

    private lateinit var vistaGestos: GridLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_gestos)

        val mainGestos = findViewById<androidx.constraintlayout.motion.widget.MotionLayout>(R.id.mainGestos)
        ViewCompat.setOnApplyWindowInsetsListener(mainGestos) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        vistaGestos = findViewById(R.id.vistaGestos)

        configurarBottomBarCompose()
        cargarGestos()
    }

    private fun configurarBottomBarCompose() {
        val composeContainer = findViewById<FrameLayout>(R.id.bottom_bar_container)
        val composeView = ComposeView(this).apply {
            setContent {
                BottomBarWithFab(
                    onHomeClick = {
                        val intent = Intent(this@Gestos, HomeActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        startActivity(intent)
                        overridePendingTransition(0, 0)
                        finish()
                    },
                    onGesturesClick = {
                        // Ya estamos en Gestos
                    },
                    onFabClick = {
                        abrirMenuPrincipal()
                    }
                )
            }
        }
        composeContainer.addView(composeView)
    }

    private fun abrirMenuPrincipal() {
        if (supportFragmentManager.findFragmentByTag("MenuBottomSheet") != null) return

        val menuSheet = MenuBottomSheetDialog(
            onProfileClick = {
                val intent = Intent(this@Gestos, ProfileActivity::class.java)
                startActivity(intent)
            },
            onSettingsClick = {
                Snackbars.info(findViewById<View>(android.R.id.content), "Configuración próximamente", Snackbar.LENGTH_SHORT).show()
            }
        )
        menuSheet.show(supportFragmentManager, "MenuBottomSheet")
    }

    private fun cargarGestos() {
        val inflater = LayoutInflater.from(this)
        vistaGestos.removeAllViews()

        val pantallaDestiono = GestureActivity::class.java

        agregarTarjetaGesto(
            inflater,
            "Saludo",
            "Enciende las luces de la sala",
            "Bombillas (Sala)",
            android.R.drawable.ic_menu_camera,
            true,
            pantallaDestiono
        )
        agregarTarjetaGesto(
            inflater,
            "Saludo",
            "Enciende las luces de la sala",
            "Bombillas (Sala)",
            android.R.drawable.ic_menu_camera,
            true,
            pantallaDestiono
        )
        agregarTarjetaGesto(
            inflater,
            "Saludo",
            "Enciende las luces de la sala",
            "Bombillas (Sala)",
            android.R.drawable.ic_menu_camera,
            true,
            pantallaDestiono
        )
        agregarTarjetaGesto(
            inflater,
            "Saludo",
            "Enciende las luces de la sala",
            "Bombillas (Sala)",
            android.R.drawable.ic_menu_camera,
            true,
            pantallaDestiono
        )
        agregarTarjetaGesto(
            inflater,
            "Saludo",
            "Enciende las luces de la sala",
            "Bombillas (Sala)",
            android.R.drawable.ic_menu_camera,
            true,
            pantallaDestiono
        )
        agregarTarjetaGesto(
            inflater,
            "Paz",
            "Enciende el Smart TV",
            "Smart TV (Sala)",
            android.R.drawable.ic_menu_slideshow,
            true,
            pantallaDestiono
        )

        agregarTarjetaGesto(
            inflater,
            "Pulgar arriba",
            "Activa la escena Relax",
            "Escena Relax",
            android.R.drawable.btn_star,
            false,
            pantallaDestiono
        )

        agregarTarjetaGesto(
            inflater,
            "Puño",
            "Apaga todas las luces",
            "Bombillas (Todas)",
            android.R.drawable.ic_lock_power_off,
            true,
            pantallaDestiono
        )

        vistaGestos.scheduleLayoutAnimation()
    }

    private fun agregarTarjetaGesto(
        inflater: LayoutInflater,
        nombre: String,
        descripcion: String,
        objetivo: String,
        iconRes: Int,
        estaActivo: Boolean,
        destino: Class<*>
    ) {
        val card = inflater.inflate(R.layout.item_gesture_active, vistaGestos, false)

        val tvName = card.findViewById<TextView>(R.id.tvGestureName)
        val tvDesc = card.findViewById<TextView>(R.id.tvGestureDesc)
//        val tvTarget = card.findViewById<TextView>(R.id.tvDeviceTarget)
        val ivIcon = card.findViewById<ImageView>(R.id.ivGestureIcon)
        val sw = card.findViewById<SwitchMaterial>(R.id.switchGesture)
//        val btnEdit = card.findViewById<MaterialCardView>(R.id.btnEdit)
//        val btnMore = card.findViewById<MaterialCardView>(R.id.btnMore)

        tvName.text = nombre
        tvDesc.text = descripcion
//        tvTarget.text = objetivo
        ivIcon.setImageResource(iconRes)
        sw.isChecked = estaActivo

        val contexto = card.context;

        sw.setOnCheckedChangeListener { _, isChecked ->
            val mensaje = if (isChecked) "Gesto $nombre activado" else "Gesto $nombre desactivado"
            Snackbar.make(findViewById(R.id.mainGestos), mensaje, Snackbar.LENGTH_SHORT).show()
        }

//        btnEdit.setOnClickListener {
//            Snackbar.make(it, "Editar gesto: $nombre", Snackbar.LENGTH_SHORT).show()
//            val intent = Intent(this, GestureActivity::class.java)
//            startActivity(intent)
//        }
//
//        btnMore.setOnClickListener {
//            Snackbar.make(it, "Más opciones para: $nombre", Snackbar.LENGTH_SHORT).show()
//        }

        vistaGestos.addView(card)
    }
}