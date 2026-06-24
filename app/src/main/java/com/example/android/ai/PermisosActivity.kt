package com.example.android.ai

import com.example.android.R
import com.example.android.HomeActivity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import kotlin.coroutines.resume

class PermisosActivity : AppCompatActivity() {

    // ==========================================================
    // MODELO DE PASO
    // ==========================================================

    data class PermisoPaso(
        val nombre: String,
        val descripcion: String,
        val iconRes: Int,
        val solicitar: suspend () -> Boolean,   // lanza el diálogo y espera resultado
        val estaOtorgado: () -> Boolean         // verifica el estado actual
    )

    // ==========================================================
    // COLORES
    // ==========================================================


    // ==========================================================
    // VISTAS
    // ==========================================================

    private lateinit var tvStepCounter: TextView
    private lateinit var dots: List<View>
    private lateinit var ivPermisoIcon: ImageView
    private lateinit var tvPermisoNombre: TextView
    private lateinit var tvPermisoDesc: TextView
    private lateinit var ivEstado: ImageView
    private lateinit var tvYaConcedido: TextView
    private lateinit var btnAccion: MaterialButton
    private lateinit var btnAnterior: MaterialButton
    private lateinit var btnSiguiente: MaterialButton

    // ==========================================================
    // ESTADO
    // ==========================================================

    private var pasoActual = 0
    private lateinit var pasos: List<PermisoPaso>

    // Callback para awaitar el resultado de requestPermissions
    private var permissionContinuation: ((Boolean) -> Unit)? = null

    // ==========================================================
    // LIFECYCLE
    // ==========================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Safe area: edge-to-edge ────────────────────────────
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor      = android.graphics.Color.TRANSPARENT
        window.navigationBarColor  = android.graphics.Color.TRANSPARENT

        setContentView(R.layout.activity_permisos)

        // Aplica insets (status bar arriba, nav bar abajo) al contenedor raíz
        val rootContent = findViewById<LinearLayout>(R.id.rootContent)
        ViewCompat.setOnApplyWindowInsetsListener(rootContent) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                v.paddingLeft,
                bars.top + 8,   // margen extra sobre el título
                v.paddingRight,
                bars.bottom + 8
            )
            insets
        }

        inicializarVistas()
        construirPasos()
        mostrarPaso(0)
    }

    override fun onResume() {
        super.onResume()
        // Al volver de Settings (overlay / batería), re-evalúa el paso actual
        actualizarEstadoPasoActual()
    }

    // ==========================================================
    // INICIALIZACIÓN
    // ==========================================================

    private fun inicializarVistas() {
        tvStepCounter   = findViewById(R.id.tvStepCounter)
        ivPermisoIcon   = findViewById(R.id.ivPermisoIcon)
        tvPermisoNombre = findViewById(R.id.tvPermisoNombre)
        tvPermisoDesc   = findViewById(R.id.tvPermisoDesc)
        ivEstado        = findViewById(R.id.ivEstado)
        tvYaConcedido   = findViewById(R.id.tvYaConcedido)
        btnAccion       = findViewById(R.id.btnAccion)
        btnAnterior     = findViewById(R.id.btnAnterior)
        btnSiguiente    = findViewById(R.id.btnSiguiente)

        dots = listOf(
            findViewById(R.id.dot1),
            findViewById(R.id.dot2),
            findViewById(R.id.dot3),
            findViewById(R.id.dot4),
            findViewById(R.id.dot5)
        )
    }

    // ==========================================================
    // PASOS — DEFINICIÓN suspend/await
    // ==========================================================

    private fun construirPasos() {
        pasos = listOf(

            // ── 1. Cámara ──────────────────────────────────────
            PermisoPaso(
                nombre      = "Cámara",
                descripcion = "Manordomo usa la cámara para detectar gestos de la mano en tiempo real " +
                              "con MediaPipe. Esto permite controlar dispositivos del hogar (luces, " +
                              "enchufes, audio) sin tocar la pantalla.",
                iconRes     = R.drawable.camera,
                estaOtorgado = {
                    ContextCompat.checkSelfPermission(
                        this, Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                },
                solicitar = {
                    pedirPermisoRuntime(Manifest.permission.CAMERA, requestCode = 100)
                }
            ),

            // ── 2. Bluetooth ───────────────────────────────────
            PermisoPaso(
                nombre      = "Bluetooth",
                descripcion = "Necesario para descubrir, vincular y enviar comandos a dispositivos " +
                              "inteligentes del hogar (bocinas, sensores, cerraduras) a través de " +
                              "conexión Bluetooth Low Energy (BLE).",
                iconRes     = R.drawable.bluetooth,
                estaOtorgado = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ContextCompat.checkSelfPermission(
                            this, Manifest.permission.BLUETOOTH_SCAN
                        ) == PackageManager.PERMISSION_GRANTED &&
                                ContextCompat.checkSelfPermission(
                                    this, Manifest.permission.BLUETOOTH_CONNECT
                                ) == PackageManager.PERMISSION_GRANTED
                    } else true
                },
                solicitar = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        pedirPermisosRuntime(
                            arrayOf(
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ),
                            requestCode = 101
                        )
                    } else true
                }
            ),

            // ── 3. Ubicación ───────────────────────────────────
            PermisoPaso(
                nombre      = "Ubicación",
                descripcion = "Android requiere acceso a la ubicación para escanear redes Wi-Fi y " +
                              "dispositivos Bluetooth cercanos. Manordomo usa esto únicamente para " +
                              "descubrir dispositivos en la red local, no para rastrear tu posición.",
                iconRes     = R.drawable.map_pin,
                estaOtorgado = {
                    ContextCompat.checkSelfPermission(
                        this, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                },
                solicitar = {
                    pedirPermisoRuntime(Manifest.permission.ACCESS_FINE_LOCATION, requestCode = 102)
                }
            ),

            // ── 4. Overlay ─────────────────────────────────────
            PermisoPaso(
                nombre      = "Sobre otras apps",
                descripcion = "Permite mostrar el panel de control flotante de Manordomo encima de " +
                              "cualquier aplicación que estés usando, para que puedas controlar " +
                              "dispositivos sin salir de lo que estás haciendo.",
                iconRes     = R.drawable.layers,
                estaOtorgado = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                        Settings.canDrawOverlays(this)
                    else true
                },
                solicitar = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        // Intenta abrir directamente la página de Manordomo en ajustes
                        val intentDirecto = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        try {
                            startActivity(intentDirecto)
                        } catch (e: Exception) {
                            // Fallback: abre la lista general de "Mostrar sobre otras apps"
                            // (ocurre en algunos fabricantes o versiones personalizadas de Android)
                            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                        }
                    }
                    // El resultado real llega en onResume cuando el usuario vuelve
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                        Settings.canDrawOverlays(this)
                    else true
                }
            ),

            // ── 5. Batería ─────────────────────────────────────
            PermisoPaso(
                nombre      = "Optimización de batería",
                descripcion = "Manordomo necesita ejecutarse en segundo plano para mantener activa " +
                              "la escucha de gestos y la conexión con los dispositivos del hogar. " +
                              "Sin esto, Android puede pausar la app y perder el control remoto.",
                iconRes     = R.drawable.battery_charging,
                estaOtorgado = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                        pm.isIgnoringBatteryOptimizations(packageName)
                    } else true
                },
                solicitar = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        intent.data = Uri.parse("package:$packageName")
                        startActivity(intent)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                        pm.isIgnoringBatteryOptimizations(packageName)
                    } else true
                }
            )
        )
    }

    // ==========================================================
    // WIZARD — MOSTRAR PASO
    // ==========================================================

    private fun mostrarPaso(index: Int) {
        pasoActual = index
        val paso   = pasos[index]
        val total  = pasos.size

        // ── Animación de entrada de la tarjeta ─────────────────
        val cardView = findViewById<androidx.cardview.widget.CardView>(R.id.cardPermiso)
        val fadeIn   = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        cardView.startAnimation(fadeIn)

        // ── Textos e ícono ─────────────────────────────────────
        tvStepCounter.text   = "Paso ${index + 1} de $total"
        tvPermisoNombre.text = paso.nombre
        tvPermisoDesc.text   = paso.descripcion
        ivPermisoIcon.setImageResource(paso.iconRes)
        ivPermisoIcon.setColorFilter(
            ContextCompat.getColor(this, R.color.teal_primary),
            PorterDuff.Mode.SRC_IN
        )

        // ── Dots de progreso: completados + actual en acento, pendientes en teal suave ──
        dots.forEachIndexed { i, dot ->
            val colorRes = if (i <= index) R.color.teal_primary else R.color.teal_card
            dot.background.setTint(ContextCompat.getColor(this, colorRes))
        }

        // ── Estado del permiso ─────────────────────────────────
        actualizarEstadoPasoActual()

        // ── Navegación ─────────────────────────────────────────
        btnAnterior.visibility = if (index == 0) View.INVISIBLE else View.VISIBLE

        btnAnterior.setOnClickListener {
            if (pasoActual > 0) mostrarPaso(pasoActual - 1)
        }

        btnSiguiente.text = if (index == total - 1) "Finalizar" else "Siguiente"

        btnSiguiente.setOnClickListener {
            if (index == total - 1) {
                // Último paso → ir a Home solo si todos están concedidos
                if (pasos.all { it.estaOtorgado() }) {
                    irAHome()
                }
                // Si falta alguno, btnSiguiente ya está deshabilitado (ver actualizarEstado)
            } else {
                mostrarPaso(index + 1)
            }
        }
    }

    // ==========================================================
    // ACTUALIZAR ESTADO DEL PASO ACTUAL
    // ==========================================================

    private fun actualizarEstadoPasoActual() {
        val paso      = pasos[pasoActual]
        val otorgado  = paso.estaOtorgado()
        val esUltimo  = pasoActual == pasos.size - 1

        if (otorgado) {
            // Ícono de check — Lucide circle-check, en color de acento
            ivEstado.setImageResource(R.drawable.circle_check)
            ivEstado.setColorFilter(
                ContextCompat.getColor(this, R.color.teal_primary),
                PorterDuff.Mode.SRC_IN
            )
            ivEstado.visibility     = View.VISIBLE
            tvYaConcedido.visibility = View.VISIBLE
            btnAccion.visibility    = View.GONE
            // Habilitar "Siguiente" / "Finalizar"
            btnSiguiente.isEnabled  = true
            btnSiguiente.alpha      = 1f
        } else {
            // Ícono de x — Lucide circle-x, se mantiene en rojo (error)
            ivEstado.setImageResource(R.drawable.circle_x)
            ivEstado.setColorFilter(
                ContextCompat.getColor(this, R.color.red),
                PorterDuff.Mode.SRC_IN
            )
            ivEstado.visibility     = View.VISIBLE
            tvYaConcedido.visibility = View.GONE
            btnAccion.visibility    = View.VISIBLE

            // Deshabilitar "Siguiente" hasta que se conceda
            btnSiguiente.isEnabled  = false
            btnSiguiente.alpha      = 0.4f

            btnAccion.setOnClickListener {
                lifecycleScope.launch {
                    val resultado = paso.solicitar()
                    // Para permisos runtime el resultado llega por callback;
                    // para Settings (overlay/batería) llegará en onResume.
                    // En ambos casos actualizamos la UI al regresar.
                    actualizarEstadoPasoActual()
                }
            }
        }

        // En el último paso, "Finalizar" solo se habilita si TODOS están otorgados
        if (esUltimo) {
            val todosOk = pasos.all { it.estaOtorgado() }
            btnSiguiente.isEnabled = todosOk
            btnSiguiente.alpha     = if (todosOk) 1f else 0.4f
        }
    }

    // ==========================================================
    // PERMISOS RUNTIME — suspend/await
    // ==========================================================

    /**
     * Solicita un permiso simple y suspende la coroutine hasta que
     * el usuario responde en el diálogo del sistema.
     */
    private suspend fun pedirPermisoRuntime(permiso: String, requestCode: Int): Boolean =
        suspendCancellableCoroutine { cont ->
            permissionContinuation = { granted -> cont.resume(granted) }
            ActivityCompat.requestPermissions(this, arrayOf(permiso), requestCode)
        }

    /**
     * Solicita múltiples permisos y suspende hasta que el usuario responde.
     */
    private suspend fun pedirPermisosRuntime(permisos: Array<String>, requestCode: Int): Boolean =
        suspendCancellableCoroutine { cont ->
            permissionContinuation = { granted -> cont.resume(granted) }
            ActivityCompat.requestPermissions(this, permisos, requestCode)
        }

    /**
     * El sistema llama aquí con el resultado de requestPermissions.
     * Se reanuda la coroutine suspendida y se actualiza la UI.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val allGranted = grantResults.isNotEmpty() &&
                grantResults.all { it == PackageManager.PERMISSION_GRANTED }

        permissionContinuation?.invoke(allGranted)
        permissionContinuation = null

        // La coroutine ya retomó; actualizamos la tarjeta por si acaso
        actualizarEstadoPasoActual()
    }

    // ==========================================================
    // NAVEGACIÓN FINAL
    // ==========================================================

    private fun irAHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}