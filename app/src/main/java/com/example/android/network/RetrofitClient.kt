package com.example.android.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

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
    val correo: String
)

data class UpdateUserRequest(
    val id: Int,
    val nombre: String?,
    val correo: String,
    val contrasenia: String?
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
}

object RetrofitClient {
    private const val BASE_URL = "http://192.168.100.10:5295/"

    val apiService: AuthApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApiService::class.java)
    }
}