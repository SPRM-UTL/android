package com.example.android.ai
import com.example.android.R

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.UUID

class ComboListActivity : AppCompatActivity() {

    private lateinit var rvCombos: RecyclerView
    private lateinit var fabAddCombo: FloatingActionButton
    private lateinit var adapter: ComboAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightNavigationBars = true
            isAppearanceLightStatusBars = false
        }

        setContentView(R.layout.activity_combo_list)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainComboList)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom + ime.bottom)

            val cardBack = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardBack)
            cardBack?.getChildAt(0)?.setPadding(0, systemBars.top, 0, 0)

            insets
        }

        rvCombos = findViewById(R.id.recyclerView)
        fabAddCombo = findViewById(R.id.fabAddCombo)

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        adapter = ComboAdapter(mutableListOf()) { combo ->
            val intent = Intent(this, SecuenciaConfigActivity::class.java)
            intent.putExtra("COMBO_ID", combo.id)
            startActivity(intent)
        }

        rvCombos.layoutManager = LinearLayoutManager(this)
        rvCombos.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val deletedCombo = adapter.combos[position]

                adapter.combos.removeAt(position)
                adapter.notifyItemRemoved(position)
                SecuenciaConfigManager.saveCombos(this@ComboListActivity, adapter.combos)

                com.google.android.material.snackbar.Snackbar.make(
                    findViewById(R.id.mainComboList),
                    "Combo '${deletedCombo.name}' eliminado",
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                ).apply {
                    // Mantiene la notificación por encima del FAB de forma dinámica
                    setAnchorView(fabAddCombo)

                    setAction("Deshacer") {
                        adapter.combos.add(position, deletedCombo)
                        adapter.notifyItemInserted(position)
                        SecuenciaConfigManager.saveCombos(this@ComboListActivity, adapter.combos)
                    }
                    setActionTextColor(androidx.core.content.ContextCompat.getColor(this@ComboListActivity, R.color.teal_primary))
                    view.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#073F4C"))
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
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val itemView = viewHolder.itemView
                    val paint = android.graphics.Paint().apply {
                        color = androidx.core.content.ContextCompat.getColor(this@ComboListActivity, R.color.teal_primary)
                    }
                    val icon = androidx.core.content.ContextCompat.getDrawable(this@ComboListActivity, android.R.drawable.ic_menu_delete)

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
        itemTouchHelper.attachToRecyclerView(rvCombos)

        fabAddCombo.setOnClickListener {
            val newCombo = Combo(id = UUID.randomUUID().toString(), name = "Nuevo Combo")
            adapter.combos.add(newCombo)
            SecuenciaConfigManager.saveCombos(this, adapter.combos)

            val intent = Intent(this, SecuenciaConfigActivity::class.java)
            intent.putExtra("COMBO_ID", newCombo.id)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.combos = SecuenciaConfigManager.loadCombos(this).toMutableList()
        adapter.notifyDataSetChanged()
    }
}