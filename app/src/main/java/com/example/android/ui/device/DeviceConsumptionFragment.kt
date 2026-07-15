package com.example.android.ui.device

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.android.network.AparatoConsumoPuntoResponse
import com.example.android.network.RetrofitClient
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.card.MaterialCardView

data class DesgloseItem(
    val periodoLabel: String,
    val energiaConsumidaWh: Float,
    val iconResId: Int
)

class DeviceConsumptionFragment : Fragment() {

    companion object {
        private const val ARG_DEVICE_ID = "device_id"
        // Tarifa base simulada para conversión a MXN
        private const val COSTO_POR_KWH = 0.95f

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

    private lateinit var chartPrincipal: BarChart
    private lateinit var shimmerConsumo: com.facebook.shimmer.ShimmerFrameLayout
    private lateinit var tabLayoutPeriod: TabLayout
    private lateinit var rvDesglose: RecyclerView
    private lateinit var tvTotalConsumption: TextView
    private lateinit var tvEstimatedCost: TextView
    private lateinit var tvTrendText: TextView
    private lateinit var ivTrend: ImageView
    private lateinit var tvLiveConsumption: TextView
    private lateinit var cardLiveConsumption: MaterialCardView
    private lateinit var tvDeviceName: TextView

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private var currentPuntos: List<AparatoConsumoPuntoResponse> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deviceId = arguments?.getInt(ARG_DEVICE_ID, -1) ?: -1
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_device_consumption, container, false)

        activity?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        }

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, 0, 0, systemBars.bottom)

            val header = view.findViewById<View>(R.id.headerLayout)
            if (header != null) {
                header.setPadding(
                    header.paddingLeft,
                    systemBars.top + (16 * resources.displayMetrics.density).toInt(),
                    header.paddingRight,
                    (16 * resources.displayMetrics.density).toInt()
                )
            }
            insets
        }

        tvTotalConsumption = view.findViewById(R.id.tvTotalConsumption)
        tvEstimatedCost = view.findViewById(R.id.tvEstimatedCost)
        tvTrendText = view.findViewById(R.id.tvTrendText)
        ivTrend = view.findViewById(R.id.ivTrend)
        tvLiveConsumption = view.findViewById(R.id.tvLiveConsumption)
        cardLiveConsumption = view.findViewById(R.id.cardLiveConsumption)
        tvDeviceName = view.findViewById(R.id.tvDeviceName)
        
        chartPrincipal = view.findViewById(R.id.chartPrincipal)
        shimmerConsumo = view.findViewById(R.id.shimmerConsumo)
        tabLayoutPeriod = view.findViewById(R.id.tabLayoutPeriod)
        rvDesglose = view.findViewById(R.id.rvDesglose)
        
        rvDesglose.layoutManager = LinearLayoutManager(context)

        view.findViewById<ImageButton>(R.id.btnConsumptionClose).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<FloatingActionButton>(R.id.fabExport).setOnClickListener {
            Toast.makeText(context, "Exportar PDF / CSV (Próximamente)", Toast.LENGTH_SHORT).show()
        }

        configurarGrafica()
        configurarTabs()

        // Mostrar shimmer inmediatamente
        shimmerConsumo.visibility = View.VISIBLE
        shimmerConsumo.startShimmer()
        chartPrincipal.visibility = View.INVISIBLE
        rvDesglose.visibility = View.INVISIBLE

        // Retrasamos la carga inicial de datos para no interferir con la animación de entrada
        view.postDelayed({
            if (isAdded && context != null) {
                tabLayoutPeriod.selectTab(tabLayoutPeriod.getTabAt(0))
                startPollingActual()
            }
        }, 350)

        return view
    }

    private fun configurarTabs() {
        tabLayoutPeriod.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val calendar = Calendar.getInstance()
                hastaIso = isoFormat.format(calendar.time)
                
                when (tab?.position) {
                    0 -> { // Hoy
                        currentGranularity = "envivo"
                        calendar.add(Calendar.DAY_OF_YEAR, -1)
                        desdeIso = isoFormat.format(calendar.time)
                        tvTrendText.text = "Últimas 24 horas"
                    }
                    1 -> { // Semana
                        currentGranularity = "dia"
                        calendar.add(Calendar.DAY_OF_YEAR, -7)
                        desdeIso = isoFormat.format(calendar.time)
                        tvTrendText.text = "Últimos 7 días"
                    }
                    2 -> { // Mes
                        currentGranularity = "dia"
                        calendar.add(Calendar.DAY_OF_YEAR, -30)
                        desdeIso = isoFormat.format(calendar.time)
                        tvTrendText.text = "Últimos 30 días"
                    }
                    3 -> { // Año
                        currentGranularity = "mes"
                        calendar.add(Calendar.YEAR, -1)
                        desdeIso = isoFormat.format(calendar.time)
                        tvTrendText.text = "Último año"
                    }
                }
                fetchConsumoResumen()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                onTabSelected(tab)
            }
        })
    }

    private fun configurarGrafica() {
        val colorTeal = ContextCompat.getColor(requireContext(), R.color.teal_primary)
        val tfSansSerif = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        chartPrincipal.description.isEnabled = false
        chartPrincipal.setDrawGridBackground(false)
        chartPrincipal.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chartPrincipal.xAxis.setDrawGridLines(false)
        chartPrincipal.axisRight.isEnabled = false
        chartPrincipal.legend.isEnabled = false
        chartPrincipal.setNoDataText("Cargando información...")
        chartPrincipal.setNoDataTextColor(colorTeal)
        chartPrincipal.setNoDataTextTypeface(tfSansSerif)
        
        chartPrincipal.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                if (e != null) {
                    val index = e.x.toInt()
                    if (index in currentPuntos.indices) {
                        mostrarDetalleBottomSheet(currentPuntos[index])
                    }
                }
            }

            override fun onNothingSelected() {}
        })
    }

    private fun fetchConsumoResumen() {
        shimmerConsumo.visibility = View.VISIBLE
        shimmerConsumo.startShimmer()
        chartPrincipal.visibility = View.INVISIBLE
        rvDesglose.visibility = View.INVISIBLE
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

                if (response.isSuccessful) {
                    val data = response.body()?.data
                    if (data != null && data.puntos.isNotEmpty()) {
                        shimmerConsumo.stopShimmer()
                        shimmerConsumo.visibility = View.GONE
                        chartPrincipal.visibility = View.VISIBLE
                        rvDesglose.visibility = View.VISIBLE
                        
                        actualizarDashboard(data.puntos, data.granularidad)
                    } else {
                        shimmerConsumo.stopShimmer()
                        shimmerConsumo.visibility = View.GONE
                        chartPrincipal.visibility = View.VISIBLE
                        rvDesglose.visibility = View.VISIBLE
                        Toast.makeText(context, "No hay datos para este período", Toast.LENGTH_SHORT).show()
                        limpiarDashboard()
                    }
                } else {
                    shimmerConsumo.stopShimmer()
                    shimmerConsumo.visibility = View.GONE
                    chartPrincipal.visibility = View.VISIBLE
                    rvDesglose.visibility = View.VISIBLE
                    Toast.makeText(context, "Error al obtener historial", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                shimmerConsumo.stopShimmer()
                shimmerConsumo.visibility = View.GONE
                chartPrincipal.visibility = View.VISIBLE
                rvDesglose.visibility = View.VISIBLE
                Toast.makeText(context, "Error de red: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun startPollingActual() {
        pollingJob?.cancel()
        pollingJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                try {
                    val token = requireContext().getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
                        .getString("apiToken", "") ?: return@launch
                    val response = RetrofitClient.deviceService.getConsumoActual("Bearer $token", deviceId)
                    if (response.isSuccessful) {
                        val currentW = response.body()?.data?.potenciaW ?: 0f
                        if (currentW > 0) {
                            cardLiveConsumption.visibility = View.VISIBLE
                            tvLiveConsumption.text = String.format("%.1f W", currentW)
                        } else {
                            cardLiveConsumption.visibility = View.GONE
                        }
                    }
                } catch (e: Exception) {
                    // Ignorar errores de polling
                }
                delay(5000) // Actualizar cada 5s
            }
        }
    }

    private fun limpiarDashboard() {
        chartPrincipal.clear()
        rvDesglose.adapter = DesgloseAdapter(emptyList())
        tvTotalConsumption.text = "0.00 kWh"
        tvEstimatedCost.text = "≈ $0.00 MXN"
        currentPuntos = emptyList()
    }

    private suspend fun actualizarDashboard(puntos: List<AparatoConsumoPuntoResponse>, granularidad: String) {
        currentPuntos = puntos

        // Mover el procesamiento pesado a un hilo secundario
        val resultadoProcesamiento = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val entradasEnergia = ArrayList<BarEntry>()
            val etiquetas = ArrayList<String>()
            val itemsDesglose = ArrayList<DesgloseItem>()
            var totalWh = 0f

            val formatterEntrada = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val formatterSalidaGrafica = when (granularidad) {
                "mes" -> SimpleDateFormat("MMM", Locale.getDefault())
                "envivo" -> SimpleDateFormat("HH:mm", Locale.getDefault())
                else -> SimpleDateFormat("dd/MM", Locale.getDefault())
            }
            val formatterSalidaLista = when (granularidad) {
                "mes" -> SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                "envivo" -> SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            }
            
            val iconResId = when (granularidad) {
                "envivo" -> R.drawable.ic_clock
                else -> R.drawable.ic_consumption_today
            }

            for ((index, punto) in puntos.withIndex()) {
                entradasEnergia.add(BarEntry(index.toFloat(), punto.energiaConsumidaWh))
                totalWh += punto.energiaConsumidaWh

                var labelGraf = punto.periodo
                var labelLista = punto.periodo

                try {
                    var p = punto.periodo
                    if (p.endsWith("Z")) p = p.dropLast(1)
                    val date = formatterEntrada.parse(p)
                    if (date != null) {
                        labelGraf = formatterSalidaGrafica.format(date)
                        labelLista = formatterSalidaLista.format(date)
                    }
                } catch (e: Exception) {
                    // Mantener el texto original en caso de error
                }

                etiquetas.add(labelGraf)
                itemsDesglose.add(DesgloseItem(labelLista, punto.energiaConsumidaWh, iconResId))
            }

            Triple(entradasEnergia, etiquetas, itemsDesglose) to totalWh
        }

        val (listas, totalWh) = resultadoProcesamiento
        val (entradasEnergia, etiquetas, itemsDesglose) = listas


        
        // Actualizar Cabecera
        val totalKwh = totalWh / 1000f
        tvTotalConsumption.text = String.format("%.2f kWh", totalKwh)
        
        val costoTotal = totalKwh * COSTO_POR_KWH
        tvEstimatedCost.text = String.format("≈ $%.2f MXN", costoTotal)

        // Configurar Gráfica
        val dataSetEnergia = BarDataSet(entradasEnergia, "Energía (Wh)")
        dataSetEnergia.color = android.graphics.Color.parseColor("#009688") // Teal primary
        dataSetEnergia.setDrawValues(false)
        val dataEnergia = BarData(dataSetEnergia)
        chartPrincipal.data = dataEnergia
        chartPrincipal.xAxis.valueFormatter = IndexAxisValueFormatter(etiquetas)
        chartPrincipal.xAxis.granularity = 1f
        chartPrincipal.invalidate()
        chartPrincipal.animateY(800) // Animación fluida
        
        // Actualizar Lista (reversa para ver lo más reciente arriba)
        rvDesglose.adapter = DesgloseAdapter(itemsDesglose.reversed())
    }
    
    private fun mostrarDetalleBottomSheet(punto: AparatoConsumoPuntoResponse) {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.layout_bottom_sheet_chart_detail, null)
        dialog.setContentView(view)
        
        val tvSheetTitle = view.findViewById<TextView>(R.id.tvSheetTitle)
        val tvSheetEnergy = view.findViewById<TextView>(R.id.tvSheetEnergy)
        val tvSheetPower = view.findViewById<TextView>(R.id.tvSheetPower)
        val tvSheetCost = view.findViewById<TextView>(R.id.tvSheetCost)
        
        // Formatear periodo
        val formatterEntrada = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val formatterSalida = SimpleDateFormat("dd 'de' MMMM, HH:mm", Locale.getDefault())
        try {
            var p = punto.periodo
            if (p.endsWith("Z")) p = p.dropLast(1)
            val date = formatterEntrada.parse(p)
            tvSheetTitle.text = if (date != null) formatterSalida.format(date) else punto.periodo
        } catch (e: Exception) {
            tvSheetTitle.text = punto.periodo
        }
        
        val kwh = punto.energiaConsumidaWh / 1000f
        val costo = kwh * COSTO_POR_KWH
        
        tvSheetEnergy.text = String.format("%.3f kWh", kwh)
        tvSheetPower.text = String.format("%.1f W", punto.potenciaPromedioW)
        tvSheetCost.text = String.format("$%.2f MXN", costo)
        
        view.findViewById<View>(R.id.btnSheetClose).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pollingJob?.cancel()

        activity?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, true)
            window.statusBarColor = ContextCompat.getColor(requireContext(), R.color.teal_primary)
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        }
    }
    
    // Adapter interno para el desglose
    private inner class DesgloseAdapter(
        private val items: List<DesgloseItem>
    ) : RecyclerView.Adapter<DesgloseAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvItemPeriod: TextView = view.findViewById(R.id.tvItemPeriod)
            val tvItemEnergy: TextView = view.findViewById(R.id.tvItemEnergy)
            val tvItemCost: TextView = view.findViewById(R.id.tvItemCost)
            val ivItemIcon: ImageView = view.findViewById(R.id.ivItemIcon)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_consumo_punto, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            
            holder.tvItemPeriod.text = item.periodoLabel
            holder.ivItemIcon.setImageResource(item.iconResId)
            
            val kwh = item.energiaConsumidaWh / 1000f
            holder.tvItemEnergy.text = String.format("%.3f kWh", kwh)
            
            val costo = kwh * COSTO_POR_KWH
            holder.tvItemCost.text = String.format("$%.2f", costo)
        }

        override fun getItemCount() = items.size
    }
}