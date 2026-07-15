package com.example.android.feature.ai
import com.example.android.core.ui.adapters.WizardSelectionAdapter
import com.example.android.core.ui.adapters.GestureDropdownAdapter
import com.example.android.core.db.models.Gesto


import com.example.android.R
import android.app.Activity
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
import androidx.viewpager2.widget.ViewPager2

class PasoWizardActivity : AppCompatActivity() {

    private lateinit var btnFinishWizard: Button
    private lateinit var tvToolbarTitle: TextView
    private lateinit var viewPager: ViewPager2

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

        val mainLayout = findViewById<View>(R.id.mainStepWizard)
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom + ime.bottom)
            val cardBack = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardBack)
            cardBack?.getChildAt(0)?.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        btnFinishWizard = findViewById(R.id.btnFinishWizard)
        tvToolbarTitle = findViewById(R.id.tvToolbarTitle)
        viewPager = findViewById(R.id.viewPagerWizard)

        val initialPose = intent.getStringExtra("INITIAL_POSE")
        if (initialPose != null) {
            selectedPoseName = initialPose
            val handStr = intent.getStringExtra("INITIAL_HAND") ?: ManoObjetivo.ANY.name
            selectedHand = ManoObjetivo.valueOf(handStr)
            selectedFrames = intent.getIntExtra("INITIAL_FRAMES", 15)
        } else {
            if (allPoses.isNotEmpty()) selectedPoseName = allPoses[0]
        }

        viewPager.isUserInputEnabled = false
        viewPager.adapter = WizardPagerAdapter { position, itemView ->
            when (position) {
                0 -> setupGestoStep(itemView)
                1 -> setupManoStep(itemView)
                2 -> setupVelocidadStep(itemView)
            }
        }

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener {
            handleBackNavigation()
        }

        updateToolbarAndButton(0)

        btnFinishWizard.setOnClickListener {
            handleNextNavigation()
        }
    }

    private fun setupGestoStep(view: View) {
        val rvGesto = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvGestoInner)
        val adapter = com.example.android.core.ui.adapters.WizardSelectionAdapter(
            items = allPoses,
            iconProvider = { R.drawable.ic_manordomo_sin_fondo },
            selectedItem = selectedPoseName,
            onItemClick = { selectedItem ->
                selectedPoseName = selectedItem
            }
        )
        rvGesto.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        rvGesto.adapter = adapter
    }

    private fun setupManoStep(view: View) {
        val rvMano = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvManoInner)
        val handIndex = when (selectedHand) {
            ManoObjetivo.ANY -> 0
            ManoObjetivo.LEFT -> 1
            ManoObjetivo.RIGHT -> 2
        }
        val currentHandStr = handOptions[handIndex]
        
        val adapter = com.example.android.core.ui.adapters.WizardSelectionAdapter(
            items = handOptions,
            iconProvider = { mano -> if (mano.contains("Cualquier")) null else R.drawable.ic_manordomo_sin_fondo },
            selectedItem = currentHandStr,
            onItemClick = { selectedItem ->
                selectedHand = when (handOptions.indexOf(selectedItem)) {
                    1 -> ManoObjetivo.LEFT
                    2 -> ManoObjetivo.RIGHT
                    else -> ManoObjetivo.ANY
                }
            }
        )
        rvMano.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        rvMano.adapter = adapter
    }

    private fun setupVelocidadStep(view: View) {
        val tvFramesValue = view.findViewById<TextView>(R.id.tvFramesValueInner)
        val seekBarFrames = view.findViewById<SeekBar>(R.id.seekBarFramesInner)

        seekBarFrames.progress = selectedFrames - 3
        tvFramesValue.text = "$selectedFrames cuadros"
        seekBarFrames.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                selectedFrames = progress + 3
                tvFramesValue.text = "$selectedFrames cuadros"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun updateToolbarAndButton(pageIndex: Int) {
        when (pageIndex) {
            0 -> {
                tvToolbarTitle.text = "Seleccionar Gesto (1/3)"
                btnFinishWizard.text = "Siguiente"
            }
            1 -> {
                tvToolbarTitle.text = "Mano a Usar (2/3)"
                btnFinishWizard.text = "Siguiente"
            }
            2 -> {
                tvToolbarTitle.text = "Velocidad de Reacción (3/3)"
                btnFinishWizard.text = "Guardar Gesto"
            }
        }
    }

    private fun handleNextNavigation() {
        val current = viewPager.currentItem
        if (current < 2) {
            viewPager.currentItem = current + 1
            updateToolbarAndButton(current + 1)
        } else {
            val resultIntent = Intent().apply {
                putExtra("POSE_NAME", selectedPoseName)
                putExtra("TARGET_HAND", selectedHand.name)
                putExtra("FRAMES", selectedFrames)
                intent.getStringExtra("EXTRA_TYPE")?.let { putExtra("EXTRA_TYPE", it) }
                val editIndex = intent.getIntExtra("EDIT_INDEX", -1)
                if (editIndex != -1) putExtra("EDIT_INDEX", editIndex)
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
