package com.example.android

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.android.db.AppDatabase
import com.example.android.db.Dispositivo
import com.example.android.db.Habitacion
import com.example.android.network.ApiHandler
import com.example.android.network.RetrofitClient
import com.example.android.view.Snackbars
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditDeviceFragment : Fragment() {

    companion object {
        private const val ARG_DEVICE_ID = "device_id"

        fun newInstance(deviceId: Int): EditDeviceFragment {
            val fragment = EditDeviceFragment()
            val args = Bundle()
            args.putInt(ARG_DEVICE_ID, deviceId)
            fragment.arguments = args
            return fragment
        }
    }

    private var deviceId: Int = -1
    private lateinit var db: AppDatabase
    private var existingDevice: Dispositivo? = null

    private lateinit var tvTarget: TextView
    private lateinit var etName: TextInputEditText
    private lateinit var etType: MaterialAutoCompleteTextView
    private lateinit var etCasa: MaterialAutoCompleteTextView
    private lateinit var etHabitacion: MaterialAutoCompleteTextView
    private lateinit var btnConfirm: MaterialButton
    private lateinit var btnDelete: MaterialButton
    private lateinit var ivTipoIcono: ImageView

    private var localSelectedCasaId: Int? = null
    private var localSelectedHabitacionId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deviceId = arguments?.getInt(ARG_DEVICE_ID) ?: -1
        db = AppDatabase.getDatabase(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_edit_device, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Botón regresar del nuevo diseño de barra superior sólida
        view.findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        tvTarget = view.findViewById(R.id.tvDeviceTarget)
        etName = view.findViewById(R.id.etDeviceName)
        etType = view.findViewById(R.id.etDeviceType)
        etCasa = view.findViewById(R.id.etCasa)
        etHabitacion = view.findViewById(R.id.etHabitacion)
        btnConfirm = view.findViewById(R.id.btnConfirm)
        btnDelete = view.findViewById(R.id.btnDelete)
        ivTipoIcono = view.findViewById(R.id.ivTipoIcono)

        cargarDatos()

        // Ajuste preciso de padding para contrarrestar el modo Edge-to-Edge
        val cardBack = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardBack)
        val basePaddingBottom = cardBack.paddingBottom
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(cardBack) { v, insets ->
            val statusBarInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            v.setPadding(v.paddingLeft, statusBarInsets.top, v.paddingRight, basePaddingBottom)
            insets
        }
    }

    private fun cargarDatos() {
        if (deviceId == -1) {
            parentFragmentManager.popBackStack()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val dev = db.dispositivoDao().getDispositivoById(deviceId)
            if (dev == null) {
                parentFragmentManager.popBackStack()
                return@launch
            }
            existingDevice = dev
            tvTarget.text = dev.nombreBluetooth ?: "Dispositivo Vinculado"
            etName.setText(dev.nombre)
            localSelectedHabitacionId = dev.skHabitacionId

            // Configurar tipos
            val tipos = withContext(Dispatchers.IO) {
                try {
                    val token = requireContext().getSharedPreferences("SesionApp", Context.MODE_PRIVATE).getString("apiToken", "") ?: ""
                    val response = RetrofitClient.deviceService.getTiposAparato("Bearer $token")
                    if (response.isSuccessful && !response.body()?.data.isNullOrEmpty()) {
                        response.body()!!.data.map { it.nombreTipo }
                    } else {
                        listOf("Focos", "Bocinas", "Ventilador", "Televisión", "Audífonos")
                    }
                } catch (e: Exception) {
                    listOf("Focos", "Bocinas", "Ventilador", "Televisión", "Audífonos")
                }
            }

            val adapterTipos = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, tipos)
            etType.setAdapter(adapterTipos)

            val tipoEdit = dev.tipo ?: tipos[0]
            etType.setText(tipoEdit, false)
            actualizarIconoTipo(tipoEdit, ivTipoIcono)

            etType.setOnClickListener { etType.showDropDown() }
            etType.setOnItemClickListener { _, _, _, _ ->
                actualizarIconoTipo(etType.text.toString(), ivTipoIcono)
            }

            // Configurar Casas y Habitaciones
            cargarCasasYHabitaciones()

            btnConfirm.setOnClickListener { guardarCambios() }
            btnDelete.setOnClickListener { startDeleteDevice() }
        }
    }

    private suspend fun cargarCasasYHabitaciones() {
        val casas = withContext(Dispatchers.IO) { db.casaDao().getAllCasas().firstOrNull() ?: emptyList() }
        if (casas.isEmpty()) return

        val nombresCasas = casas.map { it.nombre }
        val adapterCasas = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, nombresCasas)
        etCasa.setAdapter(adapterCasas)

        var habitacionInicial: Habitacion? = null
        if (localSelectedHabitacionId != null) {
            habitacionInicial = withContext(Dispatchers.IO) { db.habitacionDao().getHabitacionById(localSelectedHabitacionId!!) }
        }

        val casaInicial = casas.firstOrNull { c -> c.id == habitacionInicial?.skCasaId } ?: casas.first()
        localSelectedCasaId = casaInicial.id
        etCasa.setText(casaInicial.nombre, false)

        etCasa.setOnItemClickListener { _, _, position, _ ->
            localSelectedCasaId = casas[position].id
            cargarHabitaciones(casas[position].id)
        }

        cargarHabitaciones(localSelectedCasaId!!)
    }

    private fun cargarHabitaciones(casaId: Int) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            val habitaciones = withContext(Dispatchers.IO) { db.habitacionDao().getHabitacionesByCasa(casaId).firstOrNull() ?: emptyList() }
            val nombresHabitaciones = habitaciones.map { it.nombre }
            val adapterHabitaciones = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, nombresHabitaciones)
            etHabitacion.setAdapter(adapterHabitaciones)

            if (habitaciones.isNotEmpty()) {
                val habInicial = habitaciones.find { h -> h.id == localSelectedHabitacionId } ?: habitaciones.first()
                localSelectedHabitacionId = habInicial.id
                etHabitacion.setText(habInicial.nombre, false)
            } else {
                localSelectedHabitacionId = null
                etHabitacion.setText("", false)
            }

            etHabitacion.setOnItemClickListener { _, _, position, _ ->
                localSelectedHabitacionId = habitaciones[position].id
            }
        }
    }

    private fun actualizarIconoTipo(tipo: String, iv: ImageView) {
        val resId = when (tipo.lowercase()) {
            "focos", "foco", "luces" -> R.drawable.lightbulb
            "bocinas", "bocina", "audio" -> R.drawable.speaker
            "ventilador", "ventiladores" -> R.drawable.wind
            "televisión", "television", "tv" -> R.drawable.tv_minimal
            "audífonos", "audifonos" -> R.drawable.headphones
            else -> R.drawable.ic_power
        }

        iv.setImageResource(resId)

        if (resId == R.drawable.ic_power) {
            iv.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        } else {
            val colorTeal = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.teal_primary)
            iv.imageTintList = android.content.res.ColorStateList.valueOf(colorTeal)
            iv.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
        }

        iv.visibility = View.VISIBLE
    }

    private fun guardarCambios() {
        val finalName = etName.text.toString().trim()
        val finalType = etType.text.toString().trim()

        if (finalName.isEmpty()) {
            etName.error = "Ingresa un nombre"
            return
        }

        if (localSelectedHabitacionId == null) {
            etHabitacion.error = "Selecciona una habitación"
            return
        }

        val original = existingDevice ?: return

        val nuevo = original.copy(
            nombre = finalName,
            tipo = finalType.ifBlank { "General" },
            skHabitacionId = localSelectedHabitacionId,
            nombreHabitacion = etHabitacion.text.toString()
        )

        viewLifecycleOwner.lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = requireActivity(),
                showLoading = true,
                loadingTitle = "Guardando",
                loadingMessage = "Sincronizando dispositivo...",
                apiCall = {
                    val token = requireContext().getSharedPreferences("SesionApp", Context.MODE_PRIVATE).getString("apiToken", "") ?: ""
                    RetrofitClient.deviceService.updateDispositivo("Bearer $token", nuevo.id, nuevo)
                },
                onSuccess = { _ ->
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        db.dispositivoDao().insertDispositivo(nuevo)
                        withContext(Dispatchers.Main) {
                            Snackbars.success(requireActivity().findViewById(android.R.id.content), "Guardado exitoso", Toast.LENGTH_SHORT).show()
                            parentFragmentManager.popBackStack()
                        }
                    }
                },
                onError = { errorMsg ->
                    Snackbars.error(requireActivity().findViewById(android.R.id.content), errorMsg, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun startDeleteDevice() {
        val original = existingDevice ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = requireActivity(),
                showLoading = true,
                loadingTitle = "Eliminando",
                loadingMessage = "Eliminando dispositivo...",
                apiCall = {
                    val token = requireContext().getSharedPreferences("SesionApp", Context.MODE_PRIVATE).getString("apiToken", "") ?: ""
                    RetrofitClient.deviceService.deleteDispositivo("Bearer $token", original.id)
                },
                onSuccess = {
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        db.dispositivoDao().deleteDispositivo(original)
                        withContext(Dispatchers.Main) {
                            Snackbars.success(requireActivity().findViewById(android.R.id.content), "Dispositivo eliminado", Toast.LENGTH_SHORT).show()
                            parentFragmentManager.popBackStack()
                        }
                    }
                },
                onError = { errorMsg ->
                    Snackbars.error(requireActivity().findViewById(android.R.id.content), errorMsg, Toast.LENGTH_LONG).show()
                }
            )
        }
    }
}