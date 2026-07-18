package com.example.android.feature.ai.presentation.fragments
import com.example.android.feature.ai.domain.models.GestureAnalyzerConfig
import com.example.android.feature.ai.presentation.fragments.AIConfigFragment
import com.example.android.feature.ai.domain.analyzer.GestureAnalyzer
import com.example.android.feature.ai.presentation.adapters.CatalogoGestoAdapter
import com.example.android.core.network.client.RetrofitClient

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.android.R
import com.example.android.core.db.models.CatalogoGesto
import com.example.android.core.db.models.GuardarConfiguracionGestosDto
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AIConfigFragment : Fragment() {

    private lateinit var rvGestosConfig: RecyclerView
    private lateinit var progressBarConfig: ProgressBar
    private lateinit var fabSaveConfig: ExtendedFloatingActionButton
    private lateinit var adapter: CatalogoGestoAdapter
    
    private var catalogoList: MutableList<CatalogoGesto> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_ai_config, container, false)
        
        rvGestosConfig = view.findViewById(R.id.rvGestosConfig)
        progressBarConfig = view.findViewById(R.id.progressBarConfig)
        fabSaveConfig = view.findViewById(R.id.fabSaveConfig)

        rvGestosConfig.layoutManager = LinearLayoutManager(context)
        adapter = CatalogoGestoAdapter(catalogoList) { gesto, isChecked ->
            // Update local state when toggled
            gesto.isActive = isChecked
        }
        rvGestosConfig.adapter = adapter

        fabSaveConfig.setOnClickListener {
            saveConfiguration()
        }

        loadCatalogo()

        return view
    }

    private fun loadCatalogo() {
        progressBarConfig.visibility = View.VISIBLE
        val sharedPref = requireContext().getSharedPreferences("SesionApp", android.content.Context.MODE_PRIVATE)
        val tokenStr = sharedPref.getString("apiToken", "") ?: ""
        val token = "Bearer $tokenStr"
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.gestureService.getCatalogoGestos(token)
                withContext(Dispatchers.Main) {
                    progressBarConfig.visibility = View.GONE
                    if (response.isSuccessful) {
                        response.body()?.let { gestos ->
                            catalogoList.clear()
                            catalogoList.addAll(gestos)
                            adapter.updateData(catalogoList)
                            
                            // Save to local SharedPreferences so GestureAnalyzer can access it synchronously
                            saveToLocalPreferences(catalogoList)
                        }
                    } else {
                        Toast.makeText(context, "Error cargando catálogo", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBarConfig.visibility = View.GONE
                    Toast.makeText(context, "Error de red: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveConfiguration() {
        progressBarConfig.visibility = View.VISIBLE
        val sharedPref = requireContext().getSharedPreferences("SesionApp", android.content.Context.MODE_PRIVATE)
        val tokenStr = sharedPref.getString("apiToken", "") ?: ""
        val token = "Bearer $tokenStr"
        
        val configToSave = catalogoList.map {
            GuardarConfiguracionGestosDto(it.id, it.isActive)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.gestureService.saveCatalogoGestosConfig(token, configToSave)
                withContext(Dispatchers.Main) {
                    progressBarConfig.visibility = View.GONE
                    if (response.isSuccessful) {
                        Toast.makeText(context, "Configuración guardada exitosamente", Toast.LENGTH_SHORT).show()
                        // Update local preferences
                        saveToLocalPreferences(catalogoList)
                    } else {
                        Toast.makeText(context, "Error al guardar", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBarConfig.visibility = View.GONE
                    Toast.makeText(context, "Error de red: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveToLocalPreferences(gestos: List<CatalogoGesto>) {
        val sharedPref = requireActivity().getSharedPreferences("ai_gestures_config", android.content.Context.MODE_PRIVATE)
        with (sharedPref.edit()) {
            gestos.forEach {
                putBoolean(it.nombre, it.isActive)
            }
            apply()
        }
        
        // Also update the GestureAnalyzer singleton configuration immediately
        GestureAnalyzerConfig.updateConfig(gestos.associate { it.nombre to it.isActive })
    }
}
