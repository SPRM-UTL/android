package com.example.android.feature.ai
import com.example.android.core.db.init.AppDatabase

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.android.R
import com.example.android.core.db.models.Dispositivo
import kotlinx.coroutines.launch

class DispositivoWizardActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var viewPager: ViewPager2
    private lateinit var btnFinalizar: Button
    private lateinit var tvWizardTitle: TextView
    private lateinit var mainWizardLayout: ConstraintLayout

    private var dispositivoSeleccionado: Dispositivo? = null
    private var accionSeleccionada: Int = 0 // 0 = Encender, 1 = Apagar, 2 = Alternar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = Color.TRANSPARENT

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightNavigationBars = true
            isAppearanceLightStatusBars = false
        }

        setContentView(R.layout.activity_dispositivo_wizard)

        db = AppDatabase.getDatabase(this)

        mainWizardLayout = findViewById(R.id.mainWizardLayout)
        viewPager = findViewById(R.id.viewPagerDispositivo)
        btnFinalizar = findViewById(R.id.btnFinalizarWizard)
        tvWizardTitle = findViewById(R.id.tvWizardTitle)

        ViewCompat.setOnApplyWindowInsetsListener(mainWizardLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom + ime.bottom)
            val cardBack = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardBack)
            cardBack?.getChildAt(0)?.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { handleBackNavigation() }

        // Configuración del carrusel de pasos bloqueando gestos manuales
        viewPager.isUserInputEnabled = false
        viewPager.adapter = DispositivoPagerAdapter { position, itemView ->
            when (position) {
                0 -> setupListaStep(itemView)
                1 -> setupAccionStep(itemView)
            }
        }

        updateToolbarAndButton(0)

        btnFinalizar.setOnClickListener {
            handleNextNavigation()
        }
    }

    private fun setupListaStep(view: View) {
        val rvDispositivos = view.findViewById<RecyclerView>(R.id.rvDispositivosWizardInner)
        rvDispositivos.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            val listaDispositivos = db.dispositivoDao().getAllDispositivosOnce()
            if (listaDispositivos.isEmpty()) {
                Toast.makeText(this@DispositivoWizardActivity, "No tienes dispositivos agregados.", Toast.LENGTH_LONG).show()
            } else {
                rvDispositivos.adapter = DispositivoWizardAdapter(listaDispositivos) { dispositivo ->
                    dispositivoSeleccionado = dispositivo
                }
            }
        }
    }

    private fun setupAccionStep(view: View) {
        val cardEncender = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardEncender)
        val cardApagar = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardApagar)
        val cardAlternar = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardAlternar)

        val tvEncender = view.findViewById<TextView>(R.id.tvEncenderText)
        val tvApagar = view.findViewById<TextView>(R.id.tvApagarText)
        val tvAlternar = view.findViewById<TextView>(R.id.tvAlternarText)

        val ivEncender = view.findViewById<ImageView>(R.id.ivIconEncender)
        val ivApagar = view.findViewById<ImageView>(R.id.ivIconApagar)
        val ivAlternar = view.findViewById<ImageView>(R.id.ivIconAlternar)

        fun actualizarVisuals() {
            val tealPrimary = ContextCompat.getColor(this, R.color.teal_primary)
            val blanco = Color.parseColor("#FFFFFF")
            val fondoSeleccionado = Color.parseColor("#E0F2F1")
            val textoGris = Color.parseColor("#073F4C")

            // Refrescar tarjeta: Encender
            if (accionSeleccionada == 0) {
                cardEncender.setCardBackgroundColor(fondoSeleccionado)
                cardEncender.strokeColor = tealPrimary
                cardEncender.strokeWidth = (1.5 * resources.displayMetrics.density).toInt()
                cardEncender.cardElevation = (5 * resources.displayMetrics.density)
                tvEncender.setTextColor(tealPrimary)
                ivEncender.imageTintList = android.content.res.ColorStateList.valueOf(tealPrimary)
            } else {
                cardEncender.setCardBackgroundColor(blanco)
                cardEncender.strokeWidth = 0
                cardEncender.cardElevation = (2 * resources.displayMetrics.density)
                tvEncender.setTextColor(textoGris)
                ivEncender.imageTintList = android.content.res.ColorStateList.valueOf(textoGris)
            }

            // Refrescar tarjeta: Apagar
            if (accionSeleccionada == 1) {
                cardApagar.setCardBackgroundColor(fondoSeleccionado)
                cardApagar.strokeColor = tealPrimary
                cardApagar.strokeWidth = (1.5 * resources.displayMetrics.density).toInt()
                cardApagar.cardElevation = (5 * resources.displayMetrics.density)
                tvApagar.setTextColor(tealPrimary)
                ivApagar.imageTintList = android.content.res.ColorStateList.valueOf(tealPrimary)
            } else {
                cardApagar.setCardBackgroundColor(blanco)
                cardApagar.strokeWidth = 0
                cardApagar.cardElevation = (2 * resources.displayMetrics.density)
                tvApagar.setTextColor(textoGris)
                ivApagar.imageTintList = android.content.res.ColorStateList.valueOf(textoGris)
            }

            // Refrescar tarjeta: Alternar
            if (accionSeleccionada == 2) {
                cardAlternar.setCardBackgroundColor(fondoSeleccionado)
                cardAlternar.strokeColor = tealPrimary
                cardAlternar.strokeWidth = (1.5 * resources.displayMetrics.density).toInt()
                cardAlternar.cardElevation = (5 * resources.displayMetrics.density)
                tvAlternar.setTextColor(tealPrimary)
                ivAlternar.imageTintList = android.content.res.ColorStateList.valueOf(tealPrimary)
            } else {
                cardAlternar.setCardBackgroundColor(blanco)
                cardAlternar.strokeWidth = 0
                cardAlternar.cardElevation = (2 * resources.displayMetrics.density)
                tvAlternar.setTextColor(textoGris)
                ivAlternar.imageTintList = android.content.res.ColorStateList.valueOf(textoGris)
            }
        }

        // Inicializar pintado por primera vez
        actualizarVisuals()

        cardEncender.setOnClickListener {
            accionSeleccionada = 0
            actualizarVisuals()
        }

        cardApagar.setOnClickListener {
            accionSeleccionada = 1
            actualizarVisuals()
        }

        cardAlternar.setOnClickListener {
            accionSeleccionada = 2
            actualizarVisuals()
        }
    }

    private fun updateToolbarAndButton(pageIndex: Int) {
        when (pageIndex) {
            0 -> {
                tvWizardTitle.text = "Seleccionar Dispositivo (1/2)"
                btnFinalizar.text = "Siguiente"
            }
            1 -> {
                tvWizardTitle.text = "Acción a Ejecutar (2/2)"
                btnFinalizar.text = "Confirmar Selección"
            }
        }
    }

    private fun handleNextNavigation() {
        val current = viewPager.currentItem
        if (current == 0) {
            if (dispositivoSeleccionado == null) {
                Toast.makeText(this, "Por favor, selecciona un dispositivo", Toast.LENGTH_SHORT).show()
                return
            }
            viewPager.currentItem = 1
            updateToolbarAndButton(1)
        } else {
            val disp = dispositivoSeleccionado ?: return

            val resultIntent = Intent().apply {
                putExtra("DISPOSITIVO_ID", disp.id)
                putExtra("DISPOSITIVO_NOMBRE", disp.nombre ?: "Dispositivo ${disp.id}")
                putExtra("ACCION_TIPO", accionSeleccionada)
            }

            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun handleBackNavigation() {
        val current = viewPager.currentItem
        if (current > 0) {
            viewPager.currentItem = current - 1
            updateToolbarAndButton(current - 1)
        } else {
            finish()
        }
    }

    override fun onBackPressed() {
        val current = viewPager.currentItem
        if (current > 0) {
            viewPager.currentItem = current - 1
            updateToolbarAndButton(current - 1)
        } else {
            super.onBackPressed()
        }
    }
}
