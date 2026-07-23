package com.example.android.feature.gesture

import com.example.android.feature.ai.presentation.activities.AIVisionActivity
import com.example.android.feature.ai.domain.manager.SecuenciaConfigManager
import com.example.android.feature.ai.domain.manager.Combo
import com.example.android.feature.ai.presentation.activities.SecuenciaConfigActivity

import com.example.android.R

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

import java.util.UUID
import com.example.android.feature.profile.ProfileActivity
import kotlinx.coroutines.launch
import com.example.android.core.ui.adapters.AddGestoAdapter
import com.example.android.core.ui.adapters.GestosAdminAdapter

class GestosFragment : Fragment() {

    private lateinit var adapter: GestosAdminAdapter
    private lateinit var rvGestos: RecyclerView
    private lateinit var ivProfileGestos: ImageView

    // Launcher para manejar la creación/edición y refrescar la lista SOLO si se guardó
    private val createGestoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewLifecycleOwner.lifecycleScope.launch {
                val combos = SecuenciaConfigManager.loadCombos(requireContext())
                adapter.combos = combos.toMutableList()
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_gestos, container, false)

        ivProfileGestos = view.findViewById(R.id.ivProfileGestos)
        cargarFotoPerfil()

        view.findViewById<View>(R.id.profileCircle)?.setOnClickListener {
            val intent = Intent(requireContext(), ProfileActivity::class.java)
            startActivity(intent)
        }

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, 0, bars.right, bars.bottom + (90 * resources.displayMetrics.density).toInt())

            val cardSuperior = view.findViewById<View>(R.id.cardSuperior)
            if (cardSuperior != null) {
                cardSuperior.setPadding(
                    cardSuperior.paddingLeft,
                    bars.top + (4 * resources.displayMetrics.density).toInt(),
                    cardSuperior.paddingRight,
                    cardSuperior.paddingBottom
                )
            }

            val headerBg = view.findViewById<View>(R.id.headerBackground)
            if (headerBg != null) {
                val bgParams = headerBg.layoutParams as ViewGroup.LayoutParams
                bgParams.height = bars.top + (165 * resources.displayMetrics.density).toInt()
                headerBg.layoutParams = bgParams
            }
            insets
        }

        val btnMonitorIA = view.findViewById<MaterialCardView>(R.id.btnMonitorIA)
        btnMonitorIA.setOnClickListener {
            val intent = Intent(requireContext(), AIVisionActivity::class.java)
            startActivity(intent)
        }

        rvGestos = view.findViewById(R.id.rvGestos)
        rvGestos.layoutManager = GridLayoutManager(requireContext(), 2)

        adapter = GestosAdminAdapter(
            mutableListOf(),
            { combo ->
                val intent = Intent(requireContext(), SecuenciaConfigActivity::class.java).apply {
                    putExtra("COMBO_ID", combo.id)
                    putExtra("IS_NEW_COMBO", false)
                }
                createGestoLauncher.launch(intent)
            },
            { combo ->
                mostrarDialogoEliminarGesto(combo)
            }
        )

        val addGestoAdapter = AddGestoAdapter {
            val newComboId = UUID.randomUUID().toString()
            val intent = Intent(requireContext(), SecuenciaConfigActivity::class.java).apply {
                putExtra("COMBO_ID", newComboId)
                putExtra("IS_NEW_COMBO", true)
            }
            createGestoLauncher.launch(intent)
        }

        rvGestos.adapter = ConcatAdapter(addGestoAdapter, adapter)

        return view
    }

    private fun mostrarDialogoEliminarGesto(combo: Combo) {
        if (!isAdded || context == null) return

        MaterialAlertDialogBuilder(requireContext(), R.style.CustomAlertDialogTheme)
            .setTitle("Eliminar secuencia")
            .setMessage("¿Estás seguro de que deseas eliminar el gesto '${combo.name}'? Las automatizaciones vinculadas dejarán de ejecutarse.")
            .setPositiveButton("Eliminar") { dialog, _ ->
                val position = adapter.combos.indexOfFirst { it.id == combo.id }
                if (position != -1) {
                    adapter.combos.removeAt(position)
                    adapter.notifyItemRemoved(position)
                    viewLifecycleOwner.lifecycleScope.launch {
                        SecuenciaConfigManager.deleteCombo(requireContext(), combo.id)
                    }

                    view?.let { v ->
                        Snackbar.make(v, "Gesto '${combo.name}' eliminado", Snackbar.LENGTH_SHORT).show()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        cargarFotoPerfil()
        viewLifecycleOwner.lifecycleScope.launch {
            val combos = SecuenciaConfigManager.loadCombos(requireContext())
            adapter.combos = combos.toMutableList()
            adapter.notifyDataSetChanged()
        }
    }

    private fun cargarFotoPerfil() {
        val sharedPreferences = requireContext().getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val profileImageUrl = sharedPreferences.getString("profileImageUrl", null)

        if (!profileImageUrl.isNullOrBlank()) {
            ivProfileGestos.load(profileImageUrl) {
                placeholder(R.drawable.ic_manordomo_sin_fondo)
                error(R.drawable.ic_manordomo_sin_fondo)
                crossfade(true)
            }
        } else {
            ivProfileGestos.setImageResource(R.drawable.ic_manordomo_sin_fondo)
        }
    }
}