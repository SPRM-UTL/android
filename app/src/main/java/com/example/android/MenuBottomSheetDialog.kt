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
import com.example.android.network.ApiHandler
import com.example.android.network.RetrofitClient
import com.example.android.view.Snackbars
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class MenuBottomSheetDialog(
    private val appContext: Context
) : BottomSheetDialogFragment() {

    // NUEVO: callback para cuando se cierra el sheet
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
                startActivity(
                    Intent(context, ProfileActivity::class.java)
                )
                dismiss()
            }

        view.findViewById<LinearLayout>(R.id.settings)
            .setOnClickListener {
                startActivity(
                    Intent(context, SettingsActivity::class.java)
                )
                dismiss()
            }

        view.findViewById<LinearLayout>(R.id.logout)
            .setOnClickListener {
                logout()
                dismiss()
            }
    }

    private fun logout() {
        val sharedPref = appContext.getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val tokenGuardado = sharedPref.getString("apiToken", "") ?: ""

        if (tokenGuardado.isEmpty()) {
            performLocalLogout(sharedPref)
            return
        }

        lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = requireActivity(),
                showLoading = true,
                loadingTitle = "Cerrando sesión",
                loadingMessage = "Por favor espera...",
                apiCall = {
                    RetrofitClient.apiService.logout("Bearer $tokenGuardado")
                },
                onSuccess = {
                    performLocalLogout(sharedPref)
                },
                onError = {
                    performLocalLogout(sharedPref)
                }
            )
        }
    }

    private fun performLocalLogout(sharedPref: android.content.SharedPreferences) {
        sharedPref.edit().clear().apply()

        Snackbars.success(
            requireActivity().findViewById(android.R.id.content),
            "Sesión cerrada correctamente",
            Toast.LENGTH_SHORT
        ).show()

        val intent = Intent(appContext, MainActivity::class.java).apply {
            putExtra("FROM_LOGOUT", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        startActivity(intent)
        requireActivity().finish()
    }
}