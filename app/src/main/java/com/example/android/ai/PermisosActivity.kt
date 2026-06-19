package com.example.android.ai
import com.example.android.R

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermisosActivity : AppCompatActivity() {

    private lateinit var tvStatusCamera: TextView
    private lateinit var btnCamera: Button

    private lateinit var tvStatusOverlay: TextView
    private lateinit var btnOverlay: Button

    private lateinit var tvStatusBattery: TextView
    private lateinit var btnBattery: Button

    private lateinit var tvStatusBluetooth: TextView
    private lateinit var btnBluetooth: Button

    private lateinit var tvStatusLocation: TextView
    private lateinit var btnLocation: Button

    private lateinit var btnContinue: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permisos)

        tvStatusCamera = findViewById(R.id.tvStatusCamera)
        btnCamera = findViewById(R.id.btnCamera)

        tvStatusBluetooth = findViewById(R.id.tvStatusBluetooth)
        btnBluetooth = findViewById(R.id.btnBluetooth)

        tvStatusLocation = findViewById(R.id.tvStatusLocation)
        btnLocation = findViewById(R.id.btnLocation)
        
        tvStatusOverlay = findViewById(R.id.tvStatusOverlay)
        btnOverlay = findViewById(R.id.btnOverlay)

        tvStatusBattery = findViewById(R.id.tvStatusBattery)
        btnBattery = findViewById(R.id.btnBattery)

        btnContinue = findViewById(R.id.btnContinue)

        btnCamera.setOnClickListener {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
        }

        btnBluetooth.setOnClickListener {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ),
                    101
                )

            } else {

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ),
                    101
                )
            }
        }

        btnLocation.setOnClickListener {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                102
            )
        }

        btnOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        btnBattery.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }

        btnContinue.setOnClickListener {
            val intent = Intent(this, com.example.android.HomeActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun checkPermissions() {
        var allGranted = true

        // Cámara
        val cameraGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

        if (cameraGranted) {
            tvStatusCamera.text = "✅"
            btnCamera.isEnabled = false
            btnCamera.text = "OK"
        } else {
            tvStatusCamera.text = "❌"
            btnCamera.isEnabled = true
            btnCamera.text = "PEDIR"
            allGranted = false
        }

        // Bluetooth
        val bluetoothGranted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED &&

                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED

            } else {
                true
            }

        if (bluetoothGranted) {
            tvStatusBluetooth.text = "✅"
            btnBluetooth.isEnabled = false
            btnBluetooth.text = "OK"
        } else {
            tvStatusBluetooth.text = "❌"
            btnBluetooth.isEnabled = true
            btnBluetooth.text = "PEDIR"
            allGranted = false
        }

        // Ubicación
        val locationGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (locationGranted) {
            tvStatusLocation.text = "✅"
            btnLocation.isEnabled = false
            btnLocation.text = "OK"
        } else {
            tvStatusLocation.text = "❌"
            btnLocation.isEnabled = true
            btnLocation.text = "PEDIR"
            allGranted = false
        }

        // Overlay
        val overlayGranted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(this)
            } else {
                true
            }

        if (overlayGranted) {
            tvStatusOverlay.text = "✅"
            btnOverlay.isEnabled = false
            btnOverlay.text = "OK"
        } else {
            tvStatusOverlay.text = "❌"
            btnOverlay.isEnabled = true
            btnOverlay.text = "PEDIR"
            allGranted = false
        }

        // Batería
        val batteryGranted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager =
                    getSystemService(Context.POWER_SERVICE) as PowerManager

                powerManager.isIgnoringBatteryOptimizations(packageName)
            } else {
                true
            }

        if (batteryGranted) {
            tvStatusBattery.text = "✅"
            btnBattery.isEnabled = false
            btnBattery.text = "OK"
        } else {
            tvStatusBattery.text = "❌"
            btnBattery.isEnabled = true
            btnBattery.text = "PEDIR"
            allGranted = false
        }

        if (allGranted) {
            btnContinue.isEnabled = true
            btnContinue.setBackgroundColor(android.graphics.Color.parseColor("#03DAC5"))
        } else {
            btnContinue.isEnabled = false
            btnContinue.setBackgroundColor(android.graphics.Color.GRAY)
        }
    }
}
