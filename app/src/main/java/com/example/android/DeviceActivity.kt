package com.example.android

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.android.db.AppDatabase
import com.example.android.db.Dispositivo
import com.example.android.network.RetrofitClient
import com.example.android.ui.DeviceAdapter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import android.content.Context
import android.view.View
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeviceActivity : AppCompatActivity() {

    private lateinit var deviceAdapter: DeviceAdapter
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_device)

        val mainDevice = findViewById<View>(R.id.mainDevice)
        ViewCompat.setOnApplyWindowInsetsListener(mainDevice) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        db = AppDatabase.getDatabase(this)

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        setupRecyclerView()

        findViewById<FloatingActionButton>(R.id.fabAddDevice).setOnClickListener {
            showDeviceDialog(null)
        }

        // Observar Room DB
        lifecycleScope.launch {
            db.dispositivoDao().getAllDispositivos().collectLatest { dispositivos ->
                deviceAdapter.submitList(dispositivos)
            }
        }

        // Sincronizar con el Backend al abrir la pantalla
        syncDevicesFromApi()
    }

    private fun setupRecyclerView() {
        val rvDevices = findViewById<RecyclerView>(R.id.rvDevices)
        rvDevices.layoutManager = GridLayoutManager(this, 2)
        deviceAdapter = DeviceAdapter(
            onEditClick = { showDeviceDialog(it) },
            onDeleteClick = { deleteDevice(it) },
            onToggleClick = { dispositivo, isChecked ->
                Toast.makeText(this, "${dispositivo.nombre} -> $isChecked", Toast.LENGTH_SHORT).show()
            }
        )
        rvDevices.adapter = deviceAdapter
    }

    private fun syncDevicesFromApi() {
        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val token = sharedPref.getString("apiToken", "") ?: ""
        val bearer = "Bearer $token"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.deviceService.getDispositivos(bearer)
                if (response.isSuccessful) {
                    val apiDevices = response.body()?.data ?: emptyList()
                    db.dispositivoDao().deleteAllDispositivos()
                    db.dispositivoDao().insertAll(apiDevices)
                }
            } catch (e: Exception) {
                Log.e("DeviceActivity", "Error syncing devices", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DeviceActivity, "Error de red al sincronizar", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showDeviceDialog(dispositivoExistente: Dispositivo?) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setContentView(R.layout.dialog_device_form)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val etName = dialog.findViewById<TextInputEditText>(R.id.etDeviceName)
        val etType = dialog.findViewById<TextInputEditText>(R.id.etDeviceType)
        val etAction = dialog.findViewById<TextInputEditText>(R.id.etDeviceAction)
        val etCommand = dialog.findViewById<TextInputEditText>(R.id.etDeviceCommand)

        if (dispositivoExistente != null) {
            etName.setText(dispositivoExistente.nombre)
            etType.setText(dispositivoExistente.tipo)
            etAction.setText(dispositivoExistente.accion)
            etCommand.setText(dispositivoExistente.comandoBluetooth)
        }

        dialog.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.findViewById<Button>(R.id.btnSave).setOnClickListener {
            val name = etName.text.toString()
            val type = etType.text.toString()
            val action = etAction.text.toString()
            val command = etCommand.text.toString()

            if (name.isNotBlank()) {
                val nuevoDisp = Dispositivo(
                    id = dispositivoExistente?.id ?: 0,
                    nombre = name,
                    tipo = type,
                    accion = action,
                    comandoBluetooth = command,
                    icono = "ic_default"
                )
                saveDevice(nuevoDisp, isUpdate = dispositivoExistente != null)
                dialog.dismiss()
            } else {
                etName.error = "Campo requerido"
            }
        }
        dialog.show()
    }

    private fun saveDevice(dispositivo: Dispositivo, isUpdate: Boolean) {
        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val token = sharedPref.getString("apiToken", "") ?: ""
        val bearer = "Bearer $token"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = if (isUpdate) {
                    RetrofitClient.deviceService.updateDispositivo(bearer, dispositivo.id, dispositivo)
                    retrofit2.Response.success(com.example.android.network.ApiResponse(true, 200, dispositivo))
                } else {
                    RetrofitClient.deviceService.createDispositivo(bearer, dispositivo)
                }

                if (response.isSuccessful) {
                    val savedDevice = response.body()?.data
                    if (savedDevice != null) {
                        db.dispositivoDao().insertDispositivo(savedDevice)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@DeviceActivity, "Guardado exitoso", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@DeviceActivity, "Error al guardar en el servidor", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("DeviceActivity", "Error saving", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DeviceActivity, "Error de red", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteDevice(dispositivo: Dispositivo) {
        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val token = sharedPref.getString("apiToken", "") ?: ""
        val bearer = "Bearer $token"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.deviceService.deleteDispositivo(bearer, dispositivo.id)
                if (response.isSuccessful) {
                    db.dispositivoDao().deleteDispositivo(dispositivo)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@DeviceActivity, "Eliminado", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("DeviceActivity", "Error deleting", e)
            }
        }
    }
}