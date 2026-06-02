package com.example.android.view

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import com.example.android.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator

object CustomDialog {

    enum class DialogType {
        LOADING,
        SUCCESS,
        ERROR,
        WARNING,
        INFO
    }

    private lateinit var dialog: AlertDialog
    private lateinit var activity: Activity

    private lateinit var title: TextView
    private lateinit var subtitle: TextView

    private lateinit var progressBar: CircularProgressIndicator
    private lateinit var statusIcon: ImageView
    private lateinit var logoCard: CardView

    private lateinit var btnPositive: MaterialButton
    private lateinit var btnNegative: MaterialButton

    fun loadingDialog(myActivity: Activity) {
        activity = myActivity
    }

    fun showDialog(
        titleDialog: String,
        subtitleDialog: String,
        type: DialogType,
        cancelable: Boolean = false
    ) {

        dismissDialog()

        val builder = AlertDialog.Builder(activity)

        val view = LayoutInflater.from(activity)
            .inflate(R.layout.custom_dialog, null)

        title = view.findViewById(R.id.textView)
        subtitle = view.findViewById(R.id.textView2)

        progressBar = view.findViewById(R.id.progressBar2)
        statusIcon = view.findViewById(R.id.statusIcon)
        logoCard = view.findViewById(R.id.cardView)

        btnPositive = view.findViewById(R.id.btnPositive)
        btnNegative = view.findViewById(R.id.btnNegative)

        btnPositive.visibility = View.GONE
        btnNegative.visibility = View.GONE

        title.text = titleDialog
        subtitle.text = subtitleDialog

        // Reset elements
        progressBar.visibility = View.GONE
        statusIcon.visibility = View.VISIBLE
        logoCard.visibility = View.VISIBLE

        when (type) {

            DialogType.LOADING -> {
                val color = activity.getColor(R.color.teal_primary)
                progressBar.visibility = View.VISIBLE
                progressBar.show()
                statusIcon.setImageResource(R.drawable.ic_manordomo_icono)
                statusIcon.clearColorFilter()
                logoCard.setCardBackgroundColor(Color.WHITE)
                btnPositive.setBackgroundColor(color)
                title.setTextColor(color)
            }

            DialogType.SUCCESS -> {
                val color = Color.parseColor("#4CAF50")
                statusIcon.setImageResource(R.drawable.success)
                statusIcon.setColorFilter(Color.WHITE) 
                logoCard.setCardBackgroundColor(color)
                btnPositive.setBackgroundColor(color)
                title.setTextColor(color)
            }

            DialogType.ERROR -> {
                val color = Color.parseColor("#F44336")
                statusIcon.setImageResource(R.drawable.warning)
                statusIcon.setColorFilter(Color.WHITE)
                logoCard.setCardBackgroundColor(color)
                btnPositive.setBackgroundColor(color)
                title.setTextColor(color)
            }

            DialogType.WARNING -> {
                val color = Color.parseColor("#FF9800")
                statusIcon.setImageResource(R.drawable.warning)
                statusIcon.setColorFilter(Color.WHITE)
                logoCard.setCardBackgroundColor(color)
                btnPositive.setBackgroundColor(color)
                title.setTextColor(color)
            }

            DialogType.INFO -> {
                val color = Color.parseColor("#2196F3")
                statusIcon.setImageResource(R.drawable.info)
                statusIcon.setColorFilter(Color.WHITE)
                logoCard.setCardBackgroundColor(color)
                btnPositive.setBackgroundColor(color)
                title.setTextColor(color)
            }
        }

        builder.setView(view)
        builder.setCancelable(cancelable)

        dialog = builder.create()
        dialog.show()

        dialog.window?.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT)
        )
    }

    fun showErrorDialog(
        titleDialog: String,
        subtitleDialog: String,
        positiveText: String = "Reintentar",
        negativeText: String = "Regresar",
        retryAction: (() -> Unit)? = null,
        backAction: (() -> Unit)? = null
    ) {
        showDialog(
            titleDialog,
            subtitleDialog,
            DialogType.ERROR
        )

        btnPositive.visibility = if (retryAction != null) View.VISIBLE else View.GONE
        btnNegative.visibility = if (backAction != null) View.VISIBLE else View.GONE

        btnPositive.text = positiveText
        btnNegative.text = negativeText

        btnPositive.setOnClickListener {
            dismissDialog()
            retryAction?.invoke()
        }

        btnNegative.setOnClickListener {
            dismissDialog()
            backAction?.invoke()
        }
    }

    fun showSuccessDialog(
        titleDialog: String,
        subtitleDialog: String,
        acceptAction: () -> Unit
    ) {
        showDialog(
            titleDialog,
            subtitleDialog,
            DialogType.SUCCESS
        )

        btnPositive.visibility = View.VISIBLE
        btnNegative.visibility = View.GONE

        btnPositive.text = "Aceptar"

        btnPositive.setOnClickListener {
            dismissDialog()
            acceptAction()
        }
    }

    fun dismissDialog() {
        if (::dialog.isInitialized && dialog.isShowing) {
            dialog.dismiss()
        }
    }
}