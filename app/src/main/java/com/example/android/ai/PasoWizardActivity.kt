// Archivo: PasoWizardActivity.kt
package com.example.android.ai

import com.example.android.adapters.GestureDropdownAdapter
import com.example.android.R
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class PasoWizardActivity : AppCompatActivity() {

    private lateinit var btnFinishWizard: Button
    private lateinit var tvToolbarTitle: TextView
    private lateinit var spinnerGesto: AutoCompleteTextView
    private lateinit var spinnerMano: AutoCompleteTextView
    private lateinit var tvFramesValue: TextView
    private lateinit var seekBarFrames: SeekBar

    private var selectedPoseName: String = ""
    private var selectedHand: ManoObjetivo = ManoObjetivo.ANY
    private var selectedFrames: Int = 15

    private val allPoses = HandMetrics.HandPose.values().map { it.name.replace("_", " ") }
    private val handOptions = listOf("Cualquier Mano", "Mano Izquierda", "Mano Derecha")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightNavigationBars = true
            isAppearanceLightStatusBars = false
        }
        setContentView(R.layout.activity_step_wizard)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainStepWizard)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom + ime.bottom)

            val cardBack = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardBack)
            cardBack?.getChildAt(0)?.setPadding(0, systemBars.top, 0, 0)

            insets
        }

        btnFinishWizard = findViewById(R.id.btnFinishWizard)
        tvToolbarTitle = findViewById(R.id.tvToolbarTitle)
        spinnerGesto = findViewById(R.id.spinnerGesto)
        spinnerMano = findViewById(R.id.spinnerMano)
        tvFramesValue = findViewById(R.id.tvFramesValue)
        seekBarFrames = findViewById(R.id.seekBarFrames)

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        val initialPose = intent.getStringExtra("INITIAL_POSE")
        if (initialPose != null) {
            tvToolbarTitle.text = "Editar Gesto"
            selectedPoseName = initialPose
            val handStr = intent.getStringExtra("INITIAL_HAND") ?: ManoObjetivo.ANY.name
            selectedHand = ManoObjetivo.valueOf(handStr)
            selectedFrames = intent.getIntExtra("INITIAL_FRAMES", 15)
        } else {
            tvToolbarTitle.text = "Añadir Gesto"
            if (allPoses.isNotEmpty()) {
                selectedPoseName = allPoses[0]
            }
        }

        setupSpinners()
        setupSeekBar()

        btnFinishWizard.setOnClickListener {
            val resultIntent = Intent()
            resultIntent.putExtra("POSE_NAME", selectedPoseName)
            resultIntent.putExtra("TARGET_HAND", selectedHand.name)
            resultIntent.putExtra("FRAMES", selectedFrames)

            val extraType = intent.getStringExtra("EXTRA_TYPE")
            if (extraType != null) {
                resultIntent.putExtra("EXTRA_TYPE", extraType)
            }

            val editIndex = intent.getIntExtra("EDIT_INDEX", -1)
            if (editIndex != -1) {
                resultIntent.putExtra("EDIT_INDEX", editIndex)
            }

            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun setupSpinners() {
        // ====== DROPDOWN 1: GESTO (AHORA USA EL MISMO ADAPTADOR) ======
        val gestureAdapter = GestureDropdownAdapter(this, allPoses) { _ ->
            // Retorna el icono base de la app para todos los gestos de la lista
            R.drawable.ic_manordomo_sin_fondo
        }
        spinnerGesto.setAdapter(gestureAdapter)
        spinnerGesto.setText(selectedPoseName, false)
        spinnerGesto.setOnItemClickListener { _, _, position, _ ->
            selectedPoseName = allPoses[position]
        }

        // ====== DROPDOWN 2: MANO ======
        val handAdapter = GestureDropdownAdapter(this, handOptions) { mano ->
            when (mano) {
                "Mano Izquierda" -> R.drawable.ic_manordomo_sin_fondo // Puedes cambiarlo por un vector específico
                "Mano Derecha"   -> R.drawable.ic_manordomo_sin_fondo // Puedes cambiarlo por un vector específico
                else -> null // Usa el icono por defecto de Manordomo
            }
        }
        spinnerMano.setAdapter(handAdapter)

        val handIndex = when (selectedHand) {
            ManoObjetivo.ANY -> 0
            ManoObjetivo.LEFT -> 1
            ManoObjetivo.RIGHT -> 2
        }
        spinnerMano.setText(handOptions[handIndex], false)

        spinnerMano.setOnItemClickListener { _, _, position, _ ->
            selectedHand = when (position) {
                1 -> ManoObjetivo.LEFT
                2 -> ManoObjetivo.RIGHT
                else -> ManoObjetivo.ANY
            }
        }
    }

    private fun setupSeekBar() {
        seekBarFrames.progress = selectedFrames - 3
        tvFramesValue.text = "$selectedFrames cuadros"

        seekBarFrames.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                selectedFrames = progress + 3
                tvFramesValue.text = "$selectedFrames cuadros"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
}