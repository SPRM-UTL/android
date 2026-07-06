package com.example.android
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.bottomsheet.BottomSheetDialog
import coil.load
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.android.network.ApiHandler
import com.example.android.network.RetrofitClient
import com.example.android.network.UpdateUserRequest
import com.example.android.view.CustomDialog
import com.example.android.view.Snackbars
import com.example.android.view.cambiarColorStatusBar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {

    private lateinit var etNombrePerfil: TextInputEditText
    private lateinit var txtInputNombrePerfil: TextInputLayout
    private lateinit var etCorreoPerfil: TextInputEditText
    private lateinit var etContrasenaPerfil: TextInputEditText
    private lateinit var etConfirmarContrasena: TextInputEditText

    private lateinit var txtInputCorreo: TextInputLayout
    private lateinit var txtInputContrasena: TextInputLayout
    private lateinit var txtInputConfirmar: TextInputLayout
    private lateinit var btnGuardarPerfil: MaterialButton
    private lateinit var tvHola: TextView

    private lateinit var vistaRaiz : View
    private lateinit var btnLogout : Button
    private lateinit var mainProfile: MotionLayout

    private var userIdGuardado: Int = -1
    private var tokenGuardado: String = ""

    private lateinit var ivProfile: ImageView
    private lateinit var btnCambiarFoto: ImageButton
    private var photoUri: Uri? = null

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            photoUri?.let { launchUCrop(it) }
        }
    }

    private val photoPickerLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { launchUCrop(it) }
    }

    private val uCropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val resultUri = com.yalantis.ucrop.UCrop.getOutput(result.data!!)
            resultUri?.let { subirImagenPerfil(it) }
        } else if (result.resultCode == com.yalantis.ucrop.UCrop.RESULT_ERROR && result.data != null) {
            val error = com.yalantis.ucrop.UCrop.getError(result.data!!)
            Snackbars.error(vistaRaiz, "Error al recortar: ${error?.message}", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun launchUCrop(sourceUri: Uri) {
        val destinationUri = Uri.fromFile(File(cacheDir, "perfil_${System.currentTimeMillis()}.jpg"))

        val options = com.yalantis.ucrop.UCrop.Options()
        options.setCircleDimmedLayer(true)
        options.setCompressionQuality(90)
        options.setShowCropGrid(false)
        options.setToolbarTitle("Recortar foto")
        val colorPrimary = ContextCompat.getColor(this, R.color.teal_primary)
        val colorWhite = ContextCompat.getColor(this, R.color.white)
        val colorBackground = ContextCompat.getColor(this, R.color.background)

        options.setStatusBarColor(colorPrimary)
        options.setToolbarColor(colorPrimary)
        options.setToolbarWidgetColor(colorWhite)
        options.setActiveControlsWidgetColor(colorPrimary)
        options.setRootViewBackgroundColor(colorBackground)

        val uCropIntent = com.yalantis.ucrop.UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(512, 512)
            .withOptions(options)
            .getIntent(this)

        uCropLauncher.launch(uCropIntent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        // Replicado idéntico a Combos: Barra de navegación clara, barra de estado oscura con iconos blancos
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightNavigationBars = true
            isAppearanceLightStatusBars = false
        }

        setContentView(R.layout.activity_profile)

        mainProfile = findViewById(R.id.mainProfile)

        // Insets configurados exactamente con la misma solución dinámica de Combos
        ViewCompat.setOnApplyWindowInsetsListener(mainProfile) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())

            if (isKeyboardVisible) {
                mainProfile.transitionToEnd()
            } else {
                mainProfile.transitionToStart()
                if (currentFocus is TextInputEditText) {
                    currentFocus?.clearFocus()
                }
            }

            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom + ime.bottom)

            // ESTO INYECTA EL ESPACIO PERFECTO ARRIBA DE LA FLECHA Y EL TÍTULO
            val cardBack = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardBack)
            cardBack?.getChildAt(0)?.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        inicializarVistas()
        cargarIconosPerfil()

        CustomDialog.loadingDialog(this)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        tokenGuardado = sharedPref.getString("apiToken", "") ?: ""
        userIdGuardado = sharedPref.getInt("userId", -1)

        if (userIdGuardado != -1 && tokenGuardado.isNotEmpty()) {
            cargarDatosAPI()
        } else {
            Snackbar.make(mainProfile, "Error de sesión local", Snackbar.LENGTH_SHORT).show()
        }

        cargarBoton()
        cargarImagenPerfil(null)
    }

    private fun inicializarVistas() {
        vistaRaiz = findViewById(android.R.id.content)
        etNombrePerfil = findViewById(R.id.etNombrePerfil)
        txtInputNombrePerfil = findViewById(R.id.txtInputNombrePerfil)
        etCorreoPerfil = findViewById(R.id.etCorreoPerfil)
        etContrasenaPerfil = findViewById(R.id.etContrasenaPerfil)
        etConfirmarContrasena = findViewById(R.id.etConfirmarContrasena)

        txtInputCorreo = findViewById(R.id.txtInputCorreo)
        txtInputContrasena = findViewById(R.id.txtInputContrasena)
        txtInputConfirmar = findViewById(R.id.txtInputConfirmar)

        btnGuardarPerfil = findViewById(R.id.btnGuardarPerfil)
        tvHola = findViewById(R.id.tvHola)
        ivProfile = findViewById(R.id.ivProfile)
        btnCambiarFoto = findViewById(R.id.btnCambiarFoto)

        ivProfile.setOnClickListener { mostrarOpcionesFotoPerfil() }
        btnCambiarFoto.setOnClickListener { mostrarOpcionesFotoPerfil() }

        val inputs = listOf(etNombrePerfil, etCorreoPerfil, etContrasenaPerfil, etConfirmarContrasena)
        inputs.forEach { input ->
            input.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) mainProfile.transitionToEnd()
            }
            input.setOnClickListener { mainProfile.transitionToEnd() }

            input.setOnEditorActionListener { v, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                    v.id == R.id.etConfirmarContrasena) {
                    v.clearFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                    mainProfile.transitionToStart()
                }
                false
            }
        }

        val cerrarTecladoAction = View.OnClickListener {
            val focus = currentFocus
            if (focus is TextInputEditText) {
                focus.clearFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(focus.windowToken, 0)
            }
            mainProfile.transitionToStart()
        }

        mainProfile.setOnClickListener(cerrarTecladoAction)
        findViewById<View>(R.id.scrollProfile).setOnClickListener(cerrarTecladoAction)
        findViewById<View>(R.id.containerFormProfile).setOnClickListener(cerrarTecladoAction)
    }

    private fun cargarIconosPerfil() {
        findViewById<ImageButton>(R.id.btnBack)?.let {
            it.setImageResource(R.drawable.arrow_left)
        }

        findViewById<MaterialButton>(R.id.btnGuardarPerfil)?.let { botonGuardar ->
        }

        findViewById<MaterialButton>(R.id.logout)?.let { botonLogout ->
            botonLogout.setOnClickListener {
                logout()
            }
        }
    }

    private fun cargarBoton() {
        btnGuardarPerfil.setOnClickListener { view ->
            if (validarEntradas()) {
                actualizarDatosAPI(view)
            }
        }
    }

    private fun validarEntradas(): Boolean {
        val nombreInput = etNombrePerfil.text.toString().trim()
        val correoInput = etCorreoPerfil.text.toString().trim()
        val contrasenaInput = etContrasenaPerfil.text.toString().trim()
        val confirmarInput = etConfirmarContrasena.text.toString().trim()
        var isValid = true

        if (nombreInput.isEmpty()) {
            txtInputNombrePerfil.error = "El nombre no puede estar vacío"
            isValid = false
        } else {
            txtInputNombrePerfil.error = null
        }

        if (correoInput.isEmpty()) {
            txtInputCorreo.error = "El correo no puede estar vacío"
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(correoInput).matches()) {
            txtInputCorreo.error = "Ingresa un formato de correo electrónico válido"
            isValid = false
        } else {
            txtInputCorreo.error = null
        }

        val passwordRegex = Regex("""^(?=.*[A-Z])(?=.*[@$!%*?&._-])[A-Za-z\d@$!%*?&._-]{8,}$""")

        if (contrasenaInput.isNotEmpty()) {
            if (!contrasenaInput.matches(passwordRegex)) {
                txtInputContrasena.error = "Mín. 8 caracteres, 1 mayúscula y 1 símbolo"
                isValid = false
            } else {
                txtInputContrasena.error = null
            }

            if (contrasenaInput != confirmarInput) {
                txtInputConfirmar.error = "Las contraseñas no coinciden"
                isValid = false
            } else {
                txtInputConfirmar.error = null
            }
        } else {
            txtInputContrasena.error = null
            txtInputConfirmar.error = null

            if (confirmarInput.isNotEmpty()) {
                txtInputConfirmar.error = "Debes ingresar la nueva contraseña arriba"
                isValid = false
            }
        }

        return isValid
    }

    private fun cargarDatosAPI() {
        btnGuardarPerfil.isEnabled = false

        lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = this@ProfileActivity,
                showLoading = true,
                loadingTitle = "Cargando",
                loadingMessage = "Obteniendo datos del perfil...",
                apiCall = {
                    val currentToken = getSharedPreferences("SesionApp", Context.MODE_PRIVATE).getString("apiToken", "") ?: ""
                    RetrofitClient.apiService.getUsuario("Bearer $currentToken", userIdGuardado)
                },
                onSuccess = { response ->
                    val datosUsuario = response.data
                    etNombrePerfil.setText(datosUsuario.nombre)
                    etCorreoPerfil.setText(datosUsuario.correo)

                    val primerNombre = datosUsuario.nombre?.split(" ")?.firstOrNull() ?: ""
                    tvHola.text = "¡Hola, $primerNombre! \uD83D\uDC4B"

                    if (!datosUsuario.rutaImagen.isNullOrEmpty()) {
                        cargarImagenPerfil(datosUsuario.rutaImagen)
                        resolverUrlImagen(datosUsuario.rutaImagen)?.let { url ->
                            getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
                                .edit()
                                .putString("profileImageUrl", url)
                                .apply()
                        }
                    }

                    btnGuardarPerfil.isEnabled = true
                },
                onError = { errorMsg ->
                    if (errorMsg != "Sesión expirada") {
                        CustomDialog.showErrorDialog(
                            titleDialog = "Error al cargar",
                            subtitleDialog = errorMsg,
                            retryAction = { cargarDatosAPI() },
                            backAction = { finish() }
                        )
                    }
                }
            )
        }
    }

    private fun actualizarDatosAPI(view: View) {
        val nombreLimpio = etNombrePerfil.text.toString().trim()
        val correoLimpio = etCorreoPerfil.text.toString().trim()
        val contrasenaLimpia = etContrasenaPerfil.text.toString().trim()

        val request = UpdateUserRequest(
            id = userIdGuardado,
            nombre = nombreLimpio,
            correo = correoLimpio,
            contrasenia = if (contrasenaLimpia.isEmpty()) "" else contrasenaLimpia
        )

        btnGuardarPerfil.isEnabled = false

        lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = this@ProfileActivity,
                showLoading = true,
                loadingTitle = "Guardando",
                loadingMessage = "Actualizando tu información...",
                apiCall = {
                    val currentToken = getSharedPreferences("SesionApp", Context.MODE_PRIVATE).getString("apiToken", "") ?: ""
                    RetrofitClient.apiService.updateUsuario("Bearer $currentToken", userIdGuardado, request)
                },
                onSuccess = {
                    Snackbars.success(view, "Perfil actualizado correctamente", Snackbar.LENGTH_SHORT).show()
                    delay(1200)
                    finish()
                },
                onError = { errorMsg ->
                    if (errorMsg != "Sesión expirada") {
                        CustomDialog.showErrorDialog(
                            titleDialog = "Error al actualizar",
                            subtitleDialog = errorMsg,
                            retryAction = { actualizarDatosAPI(view) },
                            backAction = { finish() }
                        )
                        btnGuardarPerfil.isEnabled = true
                    }
                }
            )
        }
    }

    private fun logout() {
        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val tokenGuardado = sharedPref.getString("apiToken", "") ?: ""

        if (tokenGuardado.isEmpty()) {
            performLocalLogout(sharedPref)
            return
        }

        lifecycleScope.launch {
            ApiHandler.safeApiCall(
                activity = this@ProfileActivity,
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
            findViewById(android.R.id.content),
            "Sesión cerrada correctamente",
            Toast.LENGTH_SHORT
        ).show()

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("FROM_LOGOUT", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        CustomDialog.dismissDialog()
        super.onDestroy()
    }

    private fun mostrarOpcionesFotoPerfil() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_profile_photo_options, null)

        view.findViewById<View>(R.id.btnVerFoto).setOnClickListener {
            bottomSheetDialog.dismiss()
            mostrarFotoCompleta()
        }

        view.findViewById<View>(R.id.btnTomarFoto).setOnClickListener {
            bottomSheetDialog.dismiss()
            tomarFoto()
        }

        view.findViewById<View>(R.id.btnGaleria).setOnClickListener {
            bottomSheetDialog.dismiss()
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        bottomSheetDialog.window?.navigationBarColor = Color.WHITE
        WindowInsetsControllerCompat(bottomSheetDialog.window!!, view).isAppearanceLightNavigationBars = true
        bottomSheetDialog.show()
    }

    private fun mostrarFotoCompleta() {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_view_photo)

        val ivFullPhoto = dialog.findViewById<ImageView>(R.id.ivFullPhoto)
        val btnClose = dialog.findViewById<ImageButton>(R.id.btnCloseViewPhoto)

        val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val imageUrl = sharedPref.getString("profileImageUrl", null)

        if (!imageUrl.isNullOrEmpty()) {
            ivFullPhoto.load(imageUrl) {
                crossfade(true)
                error(R.drawable.user_filled)
            }
        } else {
            ivFullPhoto.setImageResource(R.drawable.user_filled)
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun tomarFoto() {
        val cacheFile = File(cacheDir, "camera_photo_${System.currentTimeMillis()}.jpg")
        photoUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            cacheFile
        )
        photoUri?.let { takePictureLauncher.launch(it) }
    }

    private fun subirImagenPerfil(uri: Uri) {
        btnCambiarFoto.isEnabled = false
        ivProfile.isEnabled = false

        lifecycleScope.launch {
            val imagenRecortada = File(uri.path!!)

            if (!imagenRecortada.exists()) {
                btnCambiarFoto.isEnabled = true
                ivProfile.isEnabled = true
                CustomDialog.showErrorDialog(
                    titleDialog = "Imagen no válida",
                    subtitleDialog = "No se pudo preparar la imagen seleccionada.",
                    retryAction = { mostrarOpcionesFotoPerfil() },
                    backAction = {}
                )
                return@launch
            }

            ApiHandler.safeApiCall(
                activity = this@ProfileActivity,
                showLoading = true,
                loadingTitle = "Subiendo foto",
                loadingMessage = "Actualizando tu imagen de perfil...",
                apiCall = {
                    val currentToken = getSharedPreferences("SesionApp", Context.MODE_PRIVATE).getString("apiToken", "") ?: ""
                    val requestFile = imagenRecortada.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    val imagePart = MultipartBody.Part.createFormData("imagen", imagenRecortada.name, requestFile)
                    val userIdPart = userIdGuardado.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                    RetrofitClient.apiService.uploadProfileImage("Bearer $currentToken", imagePart, userIdPart)
                },
                onSuccess = { response ->
                    val nuevaImagen = response.data.urlImagen ?: response.data.rutaImagen
                    cargarImagenPerfil(nuevaImagen)
                    resolverUrlImagen(nuevaImagen)?.let { url ->
                        getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
                            .edit()
                            .putString("profileImageUrl", url)
                            .apply()
                    }
                    Snackbars.success(vistaRaiz, "Foto actualizada correctamente", Snackbar.LENGTH_SHORT).show()
                    btnCambiarFoto.isEnabled = true
                    ivProfile.isEnabled = true
                },
                onError = { errorMsg ->
                    if (errorMsg != "Sesión expirada") {
                        CustomDialog.showErrorDialog(
                            titleDialog = "Error al subir",
                            subtitleDialog = errorMsg,
                            retryAction = { subirImagenPerfil(uri) },
                            backAction = {}
                        )
                    }
                    btnCambiarFoto.isEnabled = true
                    ivProfile.isEnabled = true
                }
            )
        }
    }

    private fun cargarImagenPerfil(rutaImagen: String?) {
        val imageUrl = resolverUrlImagen(rutaImagen)
            ?: getSharedPreferences("SesionApp", Context.MODE_PRIVATE).getString("profileImageUrl", null)

        if (!imageUrl.isNullOrEmpty()) {
            ivProfile.load(imageUrl) {
                crossfade(true)
                placeholder(R.drawable.user_filled)
                error(R.drawable.user_filled)
            }
        }
    }

    private fun resolverUrlImagen(rutaImagen: String?): String? {
        if (rutaImagen.isNullOrEmpty()) return null
        return if (rutaImagen.startsWith("http")) {
            rutaImagen
        } else {
            "${BuildConfig.BASE_URL.removeSuffix("/")}/${rutaImagen.removePrefix("/")}"
        }
    }
}