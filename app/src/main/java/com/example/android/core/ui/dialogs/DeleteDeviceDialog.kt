package com.example.android.core.ui.dialogs

import androidx.fragment.app.Fragment
import com.example.android.R
import com.example.android.core.db.Dispositivo
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DeleteDeviceDialog(
    private val fragment: Fragment,
    private val onConfirm: (Dispositivo) -> Unit
) {

    fun show(dispositivo: Dispositivo) {
        if (!fragment.isAdded || fragment.context == null) return

        MaterialAlertDialogBuilder(fragment.requireContext(), R.style.CustomAlertDialogTheme)
            .setTitle("Eliminar dispositivo")
            .setMessage("¿Estás seguro de que deseas eliminar '${dispositivo.nombre}'? Esta acción desvinculará el hardware permanentemente.")
            .setPositiveButton("Eliminar") { dialog, _ ->
                onConfirm(dispositivo)
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}
