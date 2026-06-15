package com.example.android.network

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.android.MainActivity
import com.example.android.R
import com.example.android.view.CustomDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import retrofit2.Response
import kotlin.coroutines.resume

object ApiHandler {

    suspend fun <T> safeApiCall(
        activity: Activity,
        showLoading: Boolean = true,
        loadingTitle: String = "Cargando",
        loadingMessage: String = "Por favor, espera...",
        apiCall: suspend () -> Response<T>,
        onSuccess: suspend (T) -> Unit,
        onError: suspend (String) -> Unit = {}
    ) {
        if (showLoading) {
            withContext(Dispatchers.Main) {
                CustomDialog.loadingDialog(activity)
                CustomDialog.showDialog(loadingTitle, loadingMessage, CustomDialog.DialogType.LOADING)
            }
        }

        try {
            val response = withContext(Dispatchers.IO) { apiCall() }

            withContext(Dispatchers.Main) {
                if (showLoading) CustomDialog.dismissDialog()

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        onSuccess(body)
                    } else {
                        onError("Respuesta vacía del servidor")
                    }
                } else if (response.code() == 401) {
                    val reauthResult = showReauthDialog(activity)
                    if (reauthResult) {
                        // Re-intenta la petición original con el nuevo token guardado en SharedPreferences
                        // El llamador (apiCall) debe leer el token fresco de SharedPreferences
                        safeApiCall(activity, showLoading, loadingTitle, loadingMessage, apiCall, onSuccess, onError)
                    } else {
                        onError("Sesión expirada")
                    }
                } else {
                    val errorJson = response.errorBody()?.string()
                    var parsedErrorMsg = "Error de servidor (${response.code()})"
                    if (!errorJson.isNullOrBlank()) {
                        try {
                            val jsonObject = org.json.JSONObject(errorJson)
                            if (jsonObject.has("data")) {
                                val dataObj = jsonObject.get("data")
                                if (dataObj is String) {
                                    parsedErrorMsg = dataObj
                                } else if (dataObj is org.json.JSONObject) {
                                    if (dataObj.has("mensaje")) {
                                        parsedErrorMsg = dataObj.getString("mensaje")
                                    } else {
                                        parsedErrorMsg = dataObj.toString()
                                    }
                                } else {
                                    parsedErrorMsg = dataObj.toString()
                                }
                            } else {
                                parsedErrorMsg = errorJson
                            }
                        } catch (e: Exception) {
                            parsedErrorMsg = errorJson
                        }
                    }
                    onError(parsedErrorMsg)
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                if (showLoading) CustomDialog.dismissDialog()
                onError("Error de conexión. Revisa tu red.")
            }
        }
    }

    private suspend fun showReauthDialog(activity: Activity): Boolean = suspendCancellableCoroutine { continuation ->
        val sharedPref = activity.getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val savedEmail = sharedPref.getString("userEmail", "") ?: ""

        val builder = AlertDialog.Builder(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_reauth, null)

        val etEmail = view.findViewById<TextInputEditText>(R.id.etReauthEmail)
        val etPassword = view.findViewById<TextInputEditText>(R.id.etReauthPassword)
        val btnContinue = view.findViewById<MaterialButton>(R.id.btnReauthContinue)
        val btnLogout = view.findViewById<MaterialButton>(R.id.btnReauthLogout)
        val pbReauth = view.findViewById<ProgressBar>(R.id.pbReauth)

        etEmail.setText(savedEmail)

        builder.setView(view)
        builder.setCancelable(false)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialog.setOnCancelListener {
            if (continuation.isActive) continuation.resume(false)
        }

        btnContinue.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString().trim()

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(activity, "Ingresa tu correo y contraseña", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnContinue.visibility = View.INVISIBLE
            pbReauth.visibility = View.VISIBLE
            etEmail.isEnabled = false
            etPassword.isEnabled = false

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val request = LoginRequest(email, pass)
                    val loginResponse = RetrofitClient.apiService.login(request)
                    withContext(Dispatchers.Main) {
                        if (loginResponse.isSuccessful && loginResponse.body()?.success == true) {
                            val data = loginResponse.body()!!.data
                            sharedPref.edit()
                                .putString("apiToken", data.token)
                                .putInt("userId", data.id)
                                .putString("userName", data.nombre ?: "")
                                .putString("userEmail", email)
                                .apply()

                            dialog.dismiss()
                            if (continuation.isActive) continuation.resume(true)
                        } else {
                            btnContinue.visibility = View.VISIBLE
                            pbReauth.visibility = View.GONE
                            etEmail.isEnabled = true
                            etPassword.isEnabled = true
                            Toast.makeText(activity, "Credenciales incorrectas", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        btnContinue.visibility = View.VISIBLE
                        pbReauth.visibility = View.GONE
                        etEmail.isEnabled = true
                        etPassword.isEnabled = true
                        Toast.makeText(activity, "Error de red al autenticar", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnLogout.setOnClickListener {
            dialog.dismiss()
            if (continuation.isActive) continuation.resume(false)

            sharedPref.edit().clear().apply()
            val intent = Intent(activity, MainActivity::class.java).apply {
                putExtra("FROM_LOGOUT", true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            activity.startActivity(intent)
        }

        dialog.show()

        val metrics = activity.resources.displayMetrics
        val width = (metrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
