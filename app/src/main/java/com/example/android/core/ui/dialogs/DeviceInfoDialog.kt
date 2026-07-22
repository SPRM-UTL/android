package com.example.android.core.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.example.android.feature.device.DeviceControlsActivity
import com.example.android.feature.device.EditDeviceFragment
import com.example.android.feature.device.MultiSocketActivity
import com.example.android.feature.device.VentiladorInteligenteActivity
import com.example.android.R
import com.example.android.feature.device.DeviceConsumptionFragment
import com.example.android.core.db.models.Dispositivo
import com.example.android.core.network.client.RetrofitClient
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class DeviceInfoDialog(
    private val fragment: Fragment,
    private val isDeviceConnected: (String?) -> Boolean
) {
    var currentDeviceInfoDialog: Dialog? = null
        private set
    var currentDeviceDialogMac: String? = null
        private set
    var currentDeviceDialogType: String? = null
        private set
    var tvDialogStatusRedGlobal: TextView? = null
        private set
    var statusDotInfoGlobal: MaterialCardView? = null
        private set

    fun updateConnectionStatus(connectedMacs: Set<String>) {
        val mac = currentDeviceDialogMac ?: return
        val isConnected = connectedMacs.any { it.equals(mac, ignoreCase = true) }
        
        tvDialogStatusRedGlobal?.let { tv ->
            statusDotInfoGlobal?.let { dot ->
                val tipo = currentDeviceDialogType ?: "Dispositivo"
                if (isConnected) {
                    tv.text = "$tipo en línea"
                    tv.setTextColor(Color.parseColor("#009688"))
                    dot.setCardBackgroundColor(Color.parseColor("#009688"))
                } else {
                    tv.text = "$tipo desconectado"
                    tv.setTextColor(Color.parseColor("#6F7EA8"))
                    dot.setCardBackgroundColor(Color.parseColor("#6F7EA8"))
                }
            }
        }
    }

    fun show(dispositivo: Dispositivo) {
        val dialogView = fragment.layoutInflater.inflate(R.layout.dialog_device_info, null)
        val dialog = Dialog(fragment.requireContext())
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogView.findViewById<TextView>(R.id.tvDialogNombre).text = dispositivo.nombre ?: "Desconocido"
        dialogView.findViewById<TextView>(R.id.tvDialogHabitacion).text = dispositivo.nombreHabitacion ?: "Sin asignar"
        dialogView.findViewById<TextView>(R.id.tvDialogTipo).text = dispositivo.tipo ?: "Desconocido"
        dialogView.findViewById<TextView>(R.id.tvDialogIp).text = dispositivo.ipAddress ?: "Desconocido"
        dialogView.findViewById<TextView>(R.id.tvDialogFirmware).text = "N/A"
        dialogView.findViewById<TextView>(R.id.tvDialogRssi).text = "N/A"
        dialogView.findViewById<TextView>(R.id.tvDialogUltimaConexion).text = dispositivo.fechaEstadoActualizado ?: "Desconocido"
        var tiempoActivoStr = "N/A"
        if (dispositivo.estadoEncendido == true && !dispositivo.fechaEstadoActualizado.isNullOrEmpty()) {
            try {
                val cleanDateString = dispositivo.fechaEstadoActualizado.substringBefore(".")
                val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val date = format.parse(cleanDateString)
                if (date != null) {
                    val diff = System.currentTimeMillis() - date.time
                    if (diff > 0) {
                        val hours = diff / (1000 * 60 * 60)
                        val mins = (diff / (1000 * 60)) % 60
                        tiempoActivoStr = if (hours > 0) "${hours} hora(s) ${mins} minuto(s)" else "${mins} minuto(s)"
                    } else {
                        tiempoActivoStr = "Recién encendido"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (dispositivo.estadoEncendido == false) {
            tiempoActivoStr = "Apagado"
        }
        dialogView.findViewById<TextView>(R.id.tvDialogTiempoActivo).text = tiempoActivoStr

        val tvDialogEstadoEncendido = dialogView.findViewById<TextView>(R.id.tvDialogEstadoEncendido)
        val estadoTexto = when (dispositivo.estadoEncendido) {
            true -> "Encendido"
            false -> "Apagado"
            null -> "Sin registrar"
        }
        tvDialogEstadoEncendido.text = estadoTexto

        val tvDialogCorriente = dialogView.findViewById<TextView>(R.id.tvDialogCorriente)
        val tvDialogPotencia = dialogView.findViewById<TextView>(R.id.tvDialogPotencia)
        val tvDialogEnergia = dialogView.findViewById<TextView>(R.id.tvDialogEnergia)
        val tvDialogVoltaje = dialogView.findViewById<TextView>(R.id.tvDialogVoltaje)
        val tvDialogConsumoHoy = dialogView.findViewById<TextView>(R.id.tvDialogConsumoHoy)
        val tvDialogHistorialConsumo = dialogView.findViewById<TextView>(R.id.tvDialogHistorialConsumo)

        tvDialogVoltaje?.text = "N/A"
        tvDialogConsumoHoy?.text = "N/A"

        fun formatearConsumoActual() {
            val corriente = dispositivo.corrienteA
            val potencia = dispositivo.potenciaW
            val energia = dispositivo.energiaAcumuladaWh

            tvDialogCorriente.text = corriente?.let { String.format("%.3f A", it) } ?: "Sin medición"
            tvDialogPotencia.text = potencia?.let { String.format("%.2f W", it) } ?: "Sin medición"
            tvDialogEnergia.text = energia?.let { String.format("%.3f Wh", it) } ?: "Sin medición"
        }
        formatearConsumoActual()

        val tipoDispositivo = dispositivo.tipo?.lowercase() ?: ""
        if (tipoDispositivo.contains("sockets inteligentes") || tipoDispositivo.contains("enchufe")) {
            dialogView.findViewById<TextView>(R.id.tvConsumoTitle)?.visibility = View.GONE
            dialogView.findViewById<MaterialCardView>(R.id.cvConsumoContainer)?.visibility = View.GONE
        }

        val tvDialogHistorial = dialogView.findViewById<TextView>(R.id.tvDialogHistorial)
        tvDialogHistorial.text = "Cargando historial..."

        val isConnected = isDeviceConnected(dispositivo.macBluetooth)
        val tvDialogStatusRed = dialogView.findViewById<TextView>(R.id.tvDialogStatusRed)
        val statusDotInfo = dialogView.findViewById<MaterialCardView>(R.id.statusDotInfo)

        val tipo = dispositivo.tipo ?: "Dispositivo"
        if (isConnected) {
            tvDialogStatusRed.text = "$tipo en línea"
            tvDialogStatusRed.setTextColor(Color.parseColor("#009688"))
            statusDotInfo.setCardBackgroundColor(Color.parseColor("#009688"))
        } else {
            tvDialogStatusRed.text = "$tipo desconectado"
            tvDialogStatusRed.setTextColor(Color.parseColor("#6F7EA8"))
            statusDotInfo.setCardBackgroundColor(Color.parseColor("#6F7EA8"))
        }

        dialogView.findViewById<ImageView>(R.id.btnDialogClose).setOnClickListener {
            dialog.dismiss()
        }

        currentDeviceInfoDialog = dialog
        currentDeviceDialogMac = dispositivo.macBluetooth
        currentDeviceDialogType = dispositivo.tipo
        tvDialogStatusRedGlobal = tvDialogStatusRed
        statusDotInfoGlobal = statusDotInfo

        dialog.setOnDismissListener {
            currentDeviceInfoDialog = null
            currentDeviceDialogMac = null
            currentDeviceDialogType = null
            tvDialogStatusRedGlobal = null
            statusDotInfoGlobal = null
        }

        val fabMain = dialogView.findViewById<FloatingActionButton>(R.id.fabMain)
        val llFabConsumo = dialogView.findViewById<View>(R.id.llFabConsumo)
        val llFabControles = dialogView.findViewById<View>(R.id.llFabControles)
        val llFabEditar = dialogView.findViewById<View>(R.id.llFabEditar)
        val fabOverlay = dialogView.findViewById<View>(R.id.fabOverlay)
        var isFabOpen = false

        fun toggleFab() {
            isFabOpen = !isFabOpen
            if (isFabOpen) {
                fabMain.animate().rotation(45f).setDuration(200).start()
                fabOverlay.visibility = View.VISIBLE
                fabOverlay.animate().alpha(1f).setDuration(200).start()
                
                val tipoLower = dispositivo.tipo?.lowercase() ?: ""
                val isEnchufe = tipoLower.contains("enchufe")
                val views = if (isEnchufe) listOf(llFabControles, llFabEditar) else listOf(llFabConsumo, llFabControles, llFabEditar)
                
                views.forEachIndexed { index, view ->
                    view.visibility = View.VISIBLE
                    view.alpha = 0f
                    view.translationY = 50f
                    view.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(200)
                        .setStartDelay((index * 50).toLong())
                        .start()
                }
            } else {
                fabMain.animate().rotation(0f).setDuration(200).start()
                fabOverlay.animate().alpha(0f).setDuration(200).withEndAction {
                    fabOverlay.visibility = View.GONE
                }.start()
                
                val tipoLower = dispositivo.tipo?.lowercase() ?: ""
                val isEnchufe = tipoLower.contains("enchufe")
                val views = if (isEnchufe) listOf(llFabControles, llFabEditar) else listOf(llFabConsumo, llFabControles, llFabEditar)
                
                views.forEach { view ->
                    view.animate()
                        .alpha(0f)
                        .translationY(50f)
                        .setDuration(200)
                        .withEndAction { view.visibility = View.GONE }
                        .start()
                }
            }
        }

        fabMain.setOnClickListener { toggleFab() }
        fabOverlay.setOnClickListener { if (isFabOpen) toggleFab() }
        
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP && isFabOpen) {
                toggleFab()
                true
            } else {
                false
            }
        }

        dialogView.findViewById<View>(R.id.btnDialogEditar).setOnClickListener {
            dialog.dismiss()
            fragment.requireActivity().supportFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                .add(R.id.fragment_container_overlay, EditDeviceFragment.newInstance(dispositivo.id))
                .addToBackStack("EditDevice")
                .commit()
        }

        dialogView.findViewById<View>(R.id.btnDialogControles).setOnClickListener {
            dialog.dismiss()
            val tipo = dispositivo.tipo ?: ""

            val intent = when {
                tipo.contains("Ventilador Inteligente", ignoreCase = true) ->
                    Intent(fragment.requireContext(), VentiladorInteligenteActivity::class.java).apply {
                        putExtra("EXTRA_DEVICE_ID", dispositivo.id)
                    }
                tipo.contains("MultiSocket", ignoreCase = true) ||
                tipo.contains("Regleta", ignoreCase = true) ->
                    Intent(fragment.requireContext(), MultiSocketActivity::class.java).apply {
                        putExtra("EXTRA_DEVICE_ID", dispositivo.id)
                    }
                else ->
                    Intent(fragment.requireContext(), DeviceControlsActivity::class.java).apply {
                        putExtra("EXTRA_DEVICE_ID", dispositivo.id)
                    }
            }
            fragment.startActivity(intent)
        }

        dialogView.findViewById<View>(R.id.btnDialogConsumo)?.setOnClickListener {
            dialog.hide()
            
            val fragmentManager = fragment.requireActivity().supportFragmentManager
            fragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.slide_in_bottom, R.anim.stationary, R.anim.stationary, R.anim.slide_out_bottom)
                .add(R.id.fragment_container_overlay, DeviceConsumptionFragment.newInstance(dispositivo.id))
                .addToBackStack("DeviceConsumption")
                .commit()

            fragmentManager.addOnBackStackChangedListener(object : FragmentManager.OnBackStackChangedListener {
                override fun onBackStackChanged() {
                    val currentFragment = fragmentManager.findFragmentById(R.id.fragment_container_overlay)
                    if (currentFragment !is DeviceConsumptionFragment) {
                        try {
                            dialog.show()
                        } catch (e: Exception) {}
                        fragmentManager.removeOnBackStackChangedListener(this)
                    }
                }
            })
        }

        fragment.viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = fragment.requireContext().getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
                    .getString("apiToken", "") ?: return@launch

                val consumoActualResponse = RetrofitClient.deviceService.getConsumoActual("Bearer $token", dispositivo.id)
                if (consumoActualResponse.isSuccessful) {
                    val consumo = consumoActualResponse.body()?.data
                    tvDialogCorriente.text = consumo?.corrienteA?.let { String.format("%.3f A", it) } ?: "Sin medición"
                    tvDialogPotencia.text = consumo?.potenciaW?.let { String.format("%.2f W", it) } ?: "Sin medición"
                    tvDialogEnergia.text = consumo?.energiaAcumuladaWh?.let { String.format("%.3f Wh", it) } ?: "Sin medición"
                }

                val consumoHistoricoResponse = RetrofitClient.deviceService.getConsumoHistorico("Bearer $token", dispositivo.id, 8)
                if (consumoHistoricoResponse.isSuccessful) {
                    val lecturas = consumoHistoricoResponse.body()?.data.orEmpty()
                    tvDialogHistorialConsumo.text = if (lecturas.isEmpty()) {
                        "Sin lecturas de consumo registradas."
                    } else {
                        lecturas.joinToString("\n") { lectura ->
                            String.format(
                                "%.3f A · %.2f W · %.3f Wh",
                                lectura.corrienteA,
                                lectura.potenciaW,
                                lectura.energiaWh
                            )
                        }
                    }
                } else {
                    tvDialogHistorialConsumo.text = "No se pudo cargar el historial de consumo."
                }

                val response = RetrofitClient.deviceService.getMensajesSocket("Bearer $token", dispositivo.id, 8)
                if (response.isSuccessful) {
                    val mensajes = response.body()?.data.orEmpty()
                    tvDialogHistorial.text = if (mensajes.isEmpty()) {
                        "Sin mensajes registrados todavía."
                    } else {
                        mensajes.joinToString("\n\n") { msg ->
                            val resumen = msg.comando ?: msg.payloadJson.take(120)
                            "${msg.direccion.uppercase()} · $resumen"
                        }
                    }
                } else {
                    tvDialogHistorial.text = "No se pudo cargar el historial."
                }
            } catch (_: Exception) {
                tvDialogHistorial.text = "Error al cargar el historial."
            }
        }

        dialog.show()
        val window = dialog.window
        if (window != null) {
            val displayMetrics = fragment.resources.displayMetrics
            val width = (displayMetrics.widthPixels * 0.95).toInt()
            val height = (displayMetrics.heightPixels * 0.90).toInt()
            window.setLayout(width, height)
        }
    }
}
