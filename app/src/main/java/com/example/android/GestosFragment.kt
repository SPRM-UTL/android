package com.example.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.android.ai.AIVisionActivity
import com.example.android.ai.SecuenciaConfigActivity
import com.example.android.ai.SecuenciaConfigManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.example.android.ai.Combo
import com.example.android.ui.AddGestoAdapter
import java.util.UUID

class GestosFragment : Fragment() {

    private lateinit var adapter: GestosAdminAdapter
    private lateinit var rvGestos: RecyclerView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_gestos, container, false)

        val ivProfileGestos = view.findViewById<ImageView>(R.id.ivProfileGestos)
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

        // Inicialización con el nuevo callback de click largo intermedio
        adapter = GestosAdminAdapter(
            mutableListOf(),
            { combo ->
                val intent = Intent(requireContext(), SecuenciaConfigActivity::class.java)
                intent.putExtra("COMBO_ID", combo.id)
                startActivity(intent)
            },
            { combo ->
                mostrarDialogoEliminarGesto(combo)
            },
            { combo, isActive ->
                // Manejo futuro de estado activo
            }
        )

        val addGestoAdapter = AddGestoAdapter {
            val newCombo = Combo(id = UUID.randomUUID().toString(), name = "Nuevo Gesto")
            adapter.combos.add(newCombo)
            SecuenciaConfigManager.saveCombos(requireContext(), adapter.combos)

            val intent = Intent(requireContext(), SecuenciaConfigActivity::class.java).apply {
                putExtra("COMBO_ID", newCombo.id)
            }
            startActivity(intent)
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
                    SecuenciaConfigManager.saveCombos(requireContext(), adapter.combos)

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
        val combos = SecuenciaConfigManager.loadCombos(requireContext())
        adapter.combos = combos.toMutableList()
        adapter.notifyDataSetChanged()
    }
}