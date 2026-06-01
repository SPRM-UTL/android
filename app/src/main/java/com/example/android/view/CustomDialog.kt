package com.example.android.view

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.android.R
import com.google.android.material.progressindicator.CircularProgressIndicator

object CustomDialog {

    private lateinit var dialog: AlertDialog
    private lateinit var activity: Activity

    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var progressBar: CircularProgressIndicator

    fun LoadingDialog(myActivity: Activity) {
        activity = myActivity
    }

    fun startLoadingDialog(
        titleDialog: String,
        subtitleDialog: String,
        cancelable: Boolean = false
    ) {

        val builder = AlertDialog.Builder(activity)

        val view = LayoutInflater.from(activity)
            .inflate(R.layout.custom_dialog, null)

        title = view.findViewById(R.id.textView)
        subtitle = view.findViewById(R.id.textView2)
        progressBar = view.findViewById(R.id.progressBar2)

        title.text = titleDialog
        subtitle.text = subtitleDialog

        builder.setView(view)
        builder.setCancelable(cancelable)

        dialog = builder.create()
        dialog.show()
        // Forzar fondo transparente en la ventana para eliminar las esquinas grises
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    fun updateText(
        titleDialog: String,
        subtitleDialog: String
    ) {
        if (::title.isInitialized) {
            title.text = titleDialog
            subtitle.text = subtitleDialog
        }
    }

    fun pauseLoading() {
        if (::progressBar.isInitialized) {
            progressBar.hide()
        }
    }

    fun resumeLoading() {
        if (::progressBar.isInitialized) {
            progressBar.show()
        }
    }

    fun dismissDialog() {
        if (::dialog.isInitialized) {
            dialog.dismiss()
        }
    }
}