package com.example.android.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

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

interface AuthApiService {
    @POST("api/Auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/Auth/logout")
    suspend fun logout(@Header("Authorization") token: String): Response<ResponseBody>
}

object RetrofitClient {
    // Aqui se remplaza por la IP del dispositivo
    private const val BASE_URL = "http://192.168.1.3:5295/"

    val apiService: AuthApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApiService::class.java)
    }
}