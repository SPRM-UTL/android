package com.example.android

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.android.network.RetrofitClient
import com.example.android.view.Snackbars
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class MenuBottomSheetDialog(
    private val appContext: Context
) : BottomSheetDialogFragment() {

    var onDismissCallback: (() -> Unit)? = null

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissCallback?.invoke()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.item_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.btnClose)
            .setOnClickListener {
                dismiss()
            }

        view.findViewById<LinearLayout>(R.id.profile)
            .setOnClickListener {
                startActivity(Intent(context, ProfileActivity::class.java))
                dismiss()
            }

        view.findViewById<LinearLayout>(R.id.settings)
            .setOnClickListener {
                startActivity(Intent(context, SettingsActivity::class.java))
                dismiss()
            }

        view.findViewById<LinearLayout>(R.id.logout)
            .setOnClickListener {
                logout()
            }
    }

    private fun logout() {
        val sharedPref = appContext.getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val token = sharedPref.getString("apiToken", "") ?: ""

        lifecycleScope.launch {
            try {
                if (token.isNotEmpty()) {
                    // ✅ Bearer corregido
                    RetrofitClient.apiService.logout("Bearer $token")
                }
            } catch (_: Exception) { }

            // Limpiar sesión
            sharedPref.edit()
                .putBoolean("isLoggedIn", false)
                .putString("apiToken", "")
                .apply()

            // ✅ dismiss() antes de navegar para evitar race condition con requireActivity()
            dismiss()

            val intent = Intent(appContext, MainActivity::class.java).apply {
                putExtra("FROM_LOGOUT", true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }
    }
}