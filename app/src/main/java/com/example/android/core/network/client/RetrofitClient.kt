package com.example.android.core.network.client
import com.example.android.BuildConfig

import com.example.android.core.network.api.*
import com.example.android.core.network.client.*
import com.example.android.core.network.bluetooth.*
import com.example.android.core.network.wifi.*
import com.example.android.core.network.stream.*
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
data class ApiResponse<T>(
    val success: Boolean,
    val status: Int,
    val data: T
)

data class LoginRequest(val correo: String, val contrasenia: String)

data class LoginResponse(
    val success: Boolean,
    val status: Int,
    val data: UserData
)

data class UserData(
    val id: Int,
    val nombre: String?,
    val token: String
)

data class RegisterRequest(val nombre: String, val correo: String, val contrasenia: String)

data class RegisterResponse(
    val success: Boolean,
    val status: Int,
    val data: RegisterData
)

data class RegisterData(
    val mensaje: String
)

data class UsuarioDataResponse(
    val success: Boolean,
    val status: Int,
    val data: UserProfileData
)

data class UserProfileData(
    val id: Int,
    val nombre: String?,
    val correo: String,
    @com.google.gson.annotations.SerializedName("ruta_imagen")
    val rutaImagen: String? = null
)

data class UpdateUserRequest(
    val id: Int,
    val nombre: String?,
    val correo: String,
    val contrasenia: String?,
    @com.google.gson.annotations.SerializedName("rutaImagen")
    val rutaImagen: String? = null
)

data class ProfileImageUploadResponse(
    val success: Boolean,
    val status: Int,
    val data: ProfileImageUploadData
)

data class ProfileImageUploadData(
    @com.google.gson.annotations.SerializedName("ruta_imagen")
    val rutaImagen: String?,
    @com.google.gson.annotations.SerializedName("url_imagen")
    val urlImagen: String?
)

// ---- Configuración de red del ESP32 ----

data class ConfiguracionRedRequest(
    @com.google.gson.annotations.SerializedName("ip_address")
    val ipAddress: String?,
    @com.google.gson.annotations.SerializedName("mac_address")
    val macAddress: String?,
    @com.google.gson.annotations.SerializedName("host_name")
    val hostName: String?,
    @com.google.gson.annotations.SerializedName("device_key")
    val deviceKey: String?,
    @com.google.gson.annotations.SerializedName("puerto_socket")
    val puertoSocket: Int? = 81,
    @com.google.gson.annotations.SerializedName("protocolo_socket")
    val protocoloSocket: String? = "ws",
    @com.google.gson.annotations.SerializedName("ruta_socket")
    val rutaSocket: String? = "/ws",
    @com.google.gson.annotations.SerializedName("activo")
    val activo: Boolean = true
)

data class ConfiguracionRedResponse(
    @com.google.gson.annotations.SerializedName("sk_aparato_configuracion_red_id")
    val id: Int,
    @com.google.gson.annotations.SerializedName("sk_aparato_id")
    val aparatoId: Int,
    @com.google.gson.annotations.SerializedName("ip_address")
    val ipAddress: String?,
    @com.google.gson.annotations.SerializedName("mac_address")
    val macAddress: String?,
    @com.google.gson.annotations.SerializedName("host_name")
    val hostName: String?,
    @com.google.gson.annotations.SerializedName("device_key")
    val deviceKey: String?,
    @com.google.gson.annotations.SerializedName("activo")
    val activo: Boolean
)

interface AuthApiService {
    @POST("api/Auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/Auth/logout")
    suspend fun logout(@Header("Authorization") token: String): Response<ResponseBody>

    @POST("api/Auth/register")
    suspend fun registrar(@Body request: RegisterRequest): Response<RegisterResponse>

    @GET("api/UsuariosApi/{id}")
    suspend fun getUsuario(@Header("Authorization") token: String,
                           @Path("id") id: Int): Response<UsuarioDataResponse>

    @PUT("api/UsuariosApi/{id}")
    suspend fun updateUsuario(@Header("Authorization") token: String,
                              @Path("id") id: Int,
                              @Body request: UpdateUserRequest): Response<ResponseBody>

    @Multipart
    @POST("api/UsuariosApi/perfil/imagen")
    suspend fun uploadProfileImage(@Header("Authorization") token: String,
                                   @Part imagen: MultipartBody.Part,
                                   @Part("usuarioId") usuarioId: RequestBody): Response<ProfileImageUploadResponse>

    @GET("api/UsuariosApi/{id}/voz-config")
    suspend fun getVozConfig(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<UsuarioVozConfigDto>

    @PUT("api/UsuariosApi/{id}/voz-config")
    suspend fun updateVozConfig(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body config: UsuarioVozConfigDto
    ): Response<ResponseBody>
}

data class UsuarioVozConfigDto(
    @com.google.gson.annotations.SerializedName("controlVozActivado")
    val controlVozActivado: Boolean,
    @com.google.gson.annotations.SerializedName("confirmacionHabladaActivada")
    val confirmacionHabladaActivada: Boolean,
    @com.google.gson.annotations.SerializedName("vozTipoSeleccionado")
    val vozTipoSeleccionado: String?,
    @com.google.gson.annotations.SerializedName("vozVelocidad")
    val vozVelocidad: Float?,
    @com.google.gson.annotations.SerializedName("vozIdioma")
    val vozIdioma: String?
)



object RetrofitClient {
    private val okHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val apiService: AuthApiService by lazy {
        retrofit.create(AuthApiService::class.java)
    }

    val deviceService: DeviceApiService by lazy {
        retrofit.create(DeviceApiService::class.java)
    }

    val gestureService: GestureApiService by lazy {
        retrofit.create(GestureApiService::class.java)
    }

    val casaService: CasaApiService by lazy {
        retrofit.create(CasaApiService::class.java)
    }

    val habitacionService: HabitacionApiService by lazy {
        retrofit.create(HabitacionApiService::class.java)
    }
}
