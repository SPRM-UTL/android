package com.example.android.core.network.api
import com.example.android.core.db.models.Casa

import com.example.android.core.network.api.*
import com.example.android.core.network.client.*
import com.example.android.core.network.bluetooth.*
import com.example.android.core.network.wifi.*
import com.example.android.core.network.stream.*

import com.example.android.core.db.models.Habitacion
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface HabitacionApiService {
    @GET("api/Habitaciones/Casa/{casaId}")
    suspend fun getHabitacionesByCasa(@Header("Authorization") token: String, @Path("casaId") casaId: Int): Response<ApiResponse<List<Habitacion>>>

    @POST("api/Habitaciones")
    suspend fun createHabitacion(@Header("Authorization") token: String, @Body habitacion: Habitacion): Response<ApiResponse<Habitacion>>

    @PUT("api/Habitaciones/{id}")
    suspend fun updateHabitacion(@Header("Authorization") token: String, @Path("id") id: Int, @Body habitacion: Habitacion): Response<ApiResponse<Any>>

    @DELETE("api/Habitaciones/{id}")
    suspend fun deleteHabitacion(@Header("Authorization") token: String, @Path("id") id: Int): Response<ApiResponse<Any>>
}
