package com.example.android

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.android.network.RetrofitClient
import com.example.android.view.Snackbars
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

class DeviceConsumptionFragment : Fragment() {

    companion object {
        private const val ARG_DEVICE_ID = "device_id"

        fun newInstance(deviceId: Int): DeviceConsumptionFragment {
            val fragment = DeviceConsumptionFragment()
            val args = Bundle()
            args.putInt(ARG_DEVICE_ID, deviceId)
            fragment.arguments = args
            return fragment
        }
    }

    private var deviceId: Int = -1
    private var desdeIso: String? = null
    private var hastaIso: String? = null
    private var currentGranularity = "dia"
    private var pollingJob: Job? = null

    private lateinit var chartPotencia: LineChart
    private lateinit var chartEnergia: BarChart
    private lateinit var chartCorriente: LineChart
    private lateinit var progressConsumo: ProgressBar
    private lateinit var btnDesde: MaterialButton
    private lateinit var btnHasta: MaterialButton
    private lateinit var cardRangoFechas: MaterialCardView

    private val dateFormatLocal = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deviceId = arguments?.getInt(ARG_DEVICE_ID, -1) ?: -1
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_device_consumption, container, false)

        // CORRECCIÓN AVANZADA: Forzar diseño Edge-to-Edge nativo para este fragmento
        activity?.window?.let { window ->
            // Le dice al sistema que no use paddings automáticos de ventana
            WindowCompat.setDecorFitsSystemWindows(window, false)
            // Pintamos la barra de estado transparente para que se vea el fondo de tu cabecera
            window.statusBarColor = android.graphics.Color.TRANSPARENT

            // Mantiene las letras e íconos superiores en color blanco
            val decorView = window.decorView
            WindowInsetsControllerCompat(window, decorView).isAppearanceLightStatusBars = false
        }

        chartPotencia = view.findViewById(R.id.chartPotencia)
        chartEnergia = view.findViewById(R.id.chartEnergia)
        chartCorriente = view.findViewById(R.id.chartCorriente)
        progressConsumo = view.findViewById(R.id.progressConsumo)
        btnDesde = view.findViewById(R.id.btnDesde)
        btnHasta = view.findViewById(R.id.btnHasta)
        cardRangoFechas = view.findViewById(R.id.cardRangoFechas)

        val btnClose = view.findViewById<ImageView>(R.id.btnConsumptionClose)
        btnClose.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        val toggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleGranularidad)
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentGranularity = when (checkedId) {
                    R.id.btnMes -> "mes"
                    R.id.btnEnVivo -> "envivo"
                    else -> "dia"
                }

                if (currentGranularity == "envivo") {
                    cardRangoFechas.visibility = View.GONE
                    startPolling()
                } else {
                    cardRangoFechas.visibility = View.VISIBLE
                    stopPolling()
                    fetchConsumo()
                }
            }
        }

        btnDesde.setOnClickListener {
            mostrarDatePicker { timestamp ->
                val date = Date(timestamp)
                desdeIso = isoFormat.format(date)
                btnDesde.text = "Desde: ${dateFormatLocal.format(date)}"
                fetchConsumo()
            }
        }

        btnHasta.setOnClickListener {
            mostrarDatePicker { timestamp ->
                val date = Date(timestamp)
                hastaIso = isoFormat.format(date)
                btnHasta.text = "Hasta: ${dateFormatLocal.format(date)}"
                fetchConsumo()
            }
        }

        configurarGraficas()
        fetchConsumo()

        return view
    }

    private fun mostrarDatePicker(onDateSelected: (Long) -> Unit) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Seleccionar fecha")
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            onDateSelected(selection)
        }
        picker.show(parentFragmentManager, "DATE_PICKER")
    }

    private fun startPolling() {
        stopPolling()
        pollingJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                fetchConsumo()
                delay(3000)
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopPolling()

        // REVERTIR CAMBIOS AL SALIR: Asegura que el resto de la app regrese a su comportamiento normal
        activity?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, true)
            window.statusBarColor = ContextCompat.getColor(requireContext(), R.color.teal_primary)
        }
    }

    private fun configurarGraficas() {
        val charts = listOf(chartPotencia, chartEnergia, chartCorriente)
        val colorTeal = ContextCompat.getColor(requireContext(), R.color.teal_primary)
        val tfSansSerif = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        for (chart in charts) {
            chart.description.isEnabled = false
            chart.setDrawGridBackground(false)
            chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
            chart.axisRight.isEnabled = false
            chart.legend.isEnabled = false

            chart.setNoDataText("No hay lecturas disponibles para mostrar")
            chart.setNoDataTextColor(colorTeal)
            chart.setNoDataTextTypeface(tfSansSerif)
        }
    }

    private fun fetchConsumo() {
        progressConsumo.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = requireContext().getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
                    .getString("apiToken", "") ?: return@launch

                val response = RetrofitClient.deviceService.getConsumoResumen(
                    "Bearer $token",
                    deviceId,
                    currentGranularity,
                    desdeIso,
                    hastaIso
                )

                progressConsumo.visibility = View.GONE

                if (response.isSuccessful) {
                    val data = response.body()?.data
                    if (data != null && data.puntos.isNotEmpty()) {
                        poblarGraficas(data.puntos, data.granularidad)
                    } else {
                        Toast.makeText(context, "No hay datos para el periodo seleccionado", Toast.LENGTH_SHORT).show()
                        limpiarGraficas()
                    }
                } else {
                    Toast.makeText(context, "Error al obtener historial", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                progressConsumo.visibility = View.GONE
                Toast.makeText(context, "Error de red: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun limpiarGraficas() {
        chartPotencia.clear()
        chartEnergia.clear()
        chartCorriente.clear()
    }

    private fun poblarGraficas(puntos: List<com.example.android.network.AparatoConsumoPuntoResponse>, granularidad: String) {
        val entradasPotencia = ArrayList<Entry>()
        val entradasEnergia = ArrayList<BarEntry>()
        val entradasCorriente = ArrayList<Entry>()
        val etiquetas = ArrayList<String>()

        val formatterEntrada = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val formatterSalida = when (granularidad) {
            "mes" -> SimpleDateFormat("dd/MM", Locale.getDefault())
            "envivo" -> SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            else -> SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        }

        for ((index, punto) in puntos.withIndex()) {
            entradasPotencia.add(Entry(index.toFloat(), punto.potenciaPromedioW))
            entradasEnergia.add(BarEntry(index.toFloat(), punto.energiaConsumidaWh))
            entradasCorriente.add(Entry(index.toFloat(), punto.corrientePromedioA))

            try {
                var p = punto.periodo
                if (p.endsWith("Z")) p = p.dropLast(1)

                val date = formatterEntrada.parse(p)
                etiquetas.add(if (date != null) formatterSalida.format(date) else punto.periodo)
            } catch (e: Exception) {
                etiquetas.add(punto.periodo)
            }
        }

        // Configurar Potencia (Línea)
        val dataSetPotencia = LineDataSet(entradasPotencia, "Potencia Promedio (W)")
        dataSetPotencia.color = android.graphics.Color.RED
        dataSetPotencia.setCircleColor(android.graphics.Color.RED)
        val dataPotencia = LineData(dataSetPotencia)
        chartPotencia.data = dataPotencia
        chartPotencia.xAxis.valueFormatter = IndexAxisValueFormatter(etiquetas)
        chartPotencia.xAxis.granularity = 1f
        chartPotencia.invalidate()

        // Configurar Energía (Barras)
        val dataSetEnergia = BarDataSet(entradasEnergia, "Energía (Wh)")
        dataSetEnergia.color = android.graphics.Color.parseColor("#4CAF50")
        val dataEnergia = BarData(dataSetEnergia)
        chartEnergia.data = dataEnergia
        chartEnergia.xAxis.valueFormatter = IndexAxisValueFormatter(etiquetas)
        chartEnergia.xAxis.granularity = 1f
        chartEnergia.invalidate()

        // Configurar Corriente (Línea)
        val dataSetCorriente = LineDataSet(entradasCorriente, "Corriente Promedio (A)")
        dataSetCorriente.color = android.graphics.Color.BLUE
        dataSetCorriente.setCircleColor(android.graphics.Color.BLUE)
        val dataCorriente = LineData(dataSetCorriente)
        chartCorriente.data = dataCorriente
        chartCorriente.xAxis.valueFormatter = IndexAxisValueFormatter(etiquetas)
        chartCorriente.xAxis.granularity = 1f
        chartCorriente.invalidate()
    }
}