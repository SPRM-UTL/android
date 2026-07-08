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
import androidx.recyclerview.widget.ItemTouchHelper
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

        // Configuración del click en el perfil para ir a "Mi Perfil"
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

        // Configuración del RecyclerView usando el diseño de cuadrícula (Grid de 2 columnas)
        rvGestos = view.findViewById(R.id.rvGestos)
        rvGestos.layoutManager = GridLayoutManager(requireContext(), 2)

        // Inicializamos el adaptador principal de la lista
        adapter = GestosAdminAdapter(mutableListOf(), { combo ->
            val intent = Intent(requireContext(), SecuenciaConfigActivity::class.java)
            intent.putExtra("COMBO_ID", combo.id)
            startActivity(intent)
        }, { combo, isActive ->
            // Manejo futuro de estado activo
        })

        // Creamos el adaptador de la tarjeta de agregar vinculando la lógica que antes tenía el FAB
        val addGestoAdapter = AddGestoAdapter {
            val newCombo = Combo(id = UUID.randomUUID().toString(), name = "Nuevo Gesto")
            adapter.combos.add(newCombo)
            SecuenciaConfigManager.saveCombos(requireContext(), adapter.combos)

            val intent = Intent(requireContext(), SecuenciaConfigActivity::class.java).apply {
                putExtra("COMBO_ID", newCombo.id)
            }
            startActivity(intent)
        }

        // Unimos ambos adaptadores usando ConcatAdapter para que la tarjeta de agregar aparezca primero
        rvGestos.adapter = ConcatAdapter(addGestoAdapter, adapter)

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                // Protegemos la primera tarjeta (la de agregar) para que no sea deslizable ni eliminable
                if (viewHolder.bindingAdapter is AddGestoAdapter) return 0
                return super.getSwipeDirs(recyclerView, viewHolder)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Obtenemos la posición real dentro de GestosAdminAdapter de forma segura
                val position = viewHolder.bindingAdapterPosition
                val deletedCombo = adapter.combos[position]

                adapter.combos.removeAt(position)
                adapter.notifyItemRemoved(position)
                SecuenciaConfigManager.saveCombos(requireContext(), adapter.combos)

                com.google.android.material.snackbar.Snackbar.make(
                    view,
                    "Gesto '${deletedCombo.name}' eliminado",
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                ).apply {
                    setAction("Deshacer") {
                        adapter.combos.add(position, deletedCombo)
                        adapter.notifyItemInserted(position)
                        SecuenciaConfigManager.saveCombos(requireContext(), adapter.combos)
                    }
                    setActionTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.teal_primary))
                    this.view.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#073F4C"))
                    setTextColor(android.graphics.Color.WHITE)
                    show()
                }
            }

            override fun onChildDraw(
                c: android.graphics.Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                // Evitamos pintar fondos de eliminación en el botón estático de agregar
                if (viewHolder.bindingAdapter is AddGestoAdapter) return

                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val itemView = viewHolder.itemView
                    val paint = android.graphics.Paint().apply {
                        color = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.teal_primary)
                    }
                    val icon = androidx.core.content.ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_delete)

                    if (dX > 0) {
                        val rect = android.graphics.RectF(
                            itemView.left.toFloat(), itemView.top.toFloat(),
                            itemView.left + dX, itemView.bottom.toFloat()
                        )
                        c.drawRoundRect(rect, 16 * resources.displayMetrics.density, 16 * resources.displayMetrics.density, paint)
                        icon?.let {
                            val iconMargin = (itemView.height - it.intrinsicHeight) / 2
                            val iconTop = itemView.top + iconMargin
                            val iconBottom = iconTop + it.intrinsicHeight
                            val iconLeft = itemView.left + iconMargin
                            val iconRight = iconLeft + it.intrinsicWidth
                            it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                            it.setTint(android.graphics.Color.WHITE)
                            it.draw(c)
                        }
                    } else if (dX < 0) {
                        val rect = android.graphics.RectF(
                            itemView.right + dX, itemView.top.toFloat(),
                            itemView.right.toFloat(), itemView.bottom.toFloat()
                        )
                        c.drawRoundRect(rect, 16 * resources.displayMetrics.density, 16 * resources.displayMetrics.density, paint)
                        icon?.let {
                            val iconMargin = (itemView.height - it.intrinsicHeight) / 2
                            val iconTop = itemView.top + iconMargin
                            val iconBottom = iconTop + it.intrinsicHeight
                            val iconRight = itemView.right - iconMargin
                            val iconLeft = iconRight - it.intrinsicWidth
                            it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                            it.setTint(android.graphics.Color.WHITE)
                            it.draw(c)
                        }
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        })
        itemTouchHelper.attachToRecyclerView(rvGestos)

        return view
    }

    override fun onResume() {
        super.onResume()
        val combos = SecuenciaConfigManager.loadCombos(requireContext())
        adapter.combos = combos.toMutableList()
        adapter.notifyDataSetChanged()
    }
}