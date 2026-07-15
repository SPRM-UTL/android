package com.example.android.feature.ai

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.android.R
import com.google.android.material.card.MaterialCardView

class ScheduleActivity : AppCompatActivity() {

    private lateinit var switchSchedule: SwitchCompat
    private lateinit var tvStartTime: TextView
    private lateinit var tvEndTime: TextView
    private lateinit var btnStartTime: View
    private lateinit var btnEndTime: View
    private lateinit var cardTimes: MaterialCardView

    private var startHour = 8
    private var startMinute = 0
    private var endHour = 18
    private var endMinute = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightNavigationBars = true
            isAppearanceLightStatusBars = false 
        }
        setContentView(R.layout.activity_schedule)

        // Aplicar Safe Area
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainSchedule)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom + ime.bottom)
            
            val cardBack = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardBack)
            cardBack?.getChildAt(0)?.setPadding(0, systemBars.top, 0, 0)
            
            insets
        }

        switchSchedule = findViewById(R.id.switchSchedule)
        tvStartTime = findViewById(R.id.tvStartTime)
        tvEndTime = findViewById(R.id.tvEndTime)
        btnStartTime = findViewById(R.id.btnStartTime)
        btnEndTime = findViewById(R.id.btnEndTime)
        cardTimes = findViewById(R.id.cardTimes)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        loadPrefs()

        switchSchedule.setOnCheckedChangeListener { _, isChecked ->
            cardTimes.alpha = if (isChecked) 1f else 0.5f
            btnStartTime.isEnabled = isChecked
            btnEndTime.isEnabled = isChecked
        }

        btnStartTime.setOnClickListener {
            TimePickerDialog(this, { _, hour, minute ->
                startHour = hour
                startMinute = minute
                updateTimeLabels()
            }, startHour, startMinute, true).show()
        }

        btnEndTime.setOnClickListener {
            TimePickerDialog(this, { _, hour, minute ->
                endHour = hour
                endMinute = minute
                updateTimeLabels()
            }, endHour, endMinute, true).show()
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            savePrefs()
            notifyServiceScheduleChanged()
            finish()
        }
    }

    private fun loadPrefs() {
        val enabled = PrefsManager.isScheduleEnabled(this)
        startHour = PrefsManager.getStartHour(this)
        startMinute = PrefsManager.getStartMinute(this)
        endHour = PrefsManager.getEndHour(this)
        endMinute = PrefsManager.getEndMinute(this)

        switchSchedule.isChecked = enabled
        cardTimes.alpha = if (enabled) 1f else 0.5f
        btnStartTime.isEnabled = enabled
        btnEndTime.isEnabled = enabled

        updateTimeLabels()
    }

    private fun updateTimeLabels() {
        tvStartTime.text = String.format("%02d:%02d", startHour, startMinute)
        tvEndTime.text = String.format("%02d:%02d", endHour, endMinute)
    }

    private fun savePrefs() {
        PrefsManager.setScheduleEnabled(this, switchSchedule.isChecked)
        PrefsManager.setStartHour(this, startHour)
        PrefsManager.setStartMinute(this, startMinute)
        PrefsManager.setEndHour(this, endHour)
        PrefsManager.setEndMinute(this, endMinute)
    }

    private fun notifyServiceScheduleChanged() {
        if (CameraSharedState.isServiceRunning) {
            val intent = Intent(this, BackgroundCameraService::class.java).apply {
                action = BackgroundCameraService.ACTION_UPDATE_SCHEDULE
            }
            startService(intent)
        }
    }
}
