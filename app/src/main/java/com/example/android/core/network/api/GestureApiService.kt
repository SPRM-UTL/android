package com.example.android.core.network.api
import com.example.android.core.db.models.GuardarConfiguracionGestosDto

import com.example.android.core.network.api.*
import com.example.android.core.network.client.*
import com.example.android.core.network.bluetooth.*
import com.example.android.core.network.wifi.*
import com.example.android.core.network.stream.*
import com.example.android.core.db.models.CatalogoGesto

import com.example.android.core.db.models.Gesto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface GestureApiService {
    @GET("api/Dim_Gestos")
    suspend fun getGestos(@Header("Authorization") token: String): Response<ApiResponse<List<Gesto>>>

    @GET("api/Dim_Gestos/{id}")
    suspend fun getGesto(@Header("Authorization") token: String, @Path("id") id: Int): Response<ApiResponse<Gesto>>

    @POST("api/Dim_Gestos")
    suspend fun createGesto(@Header("Authorization") token: String, @Body gesto: Gesto): Response<ApiResponse<Gesto>>

    @PUT("api/Dim_Gestos/{id}")
    suspend fun updateGesto(@Header("Authorization") token: String, @Path("id") id: Int, @Body gesto: Gesto): Response<ResponseBody>

    @DELETE("api/Dim_Gestos/{id}")
    suspend fun deleteGesto(@Header("Authorization") token: String, @Path("id") id: Int): Response<ResponseBody>

    @GET("api/catalogo_gestos")
    suspend fun getCatalogoGestos(@Header("Authorization") token: String): Response<List<com.example.android.core.db.models.CatalogoGesto>>

    @POST("api/catalogo_gestos/config")
    suspend fun saveCatalogoGestosConfig(@Header("Authorization") token: String, @Body config: List<com.example.android.core.db.models.GuardarConfiguracionGestosDto>): Response<ResponseBody>
}
