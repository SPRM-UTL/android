package com.example.android.network

import com.example.android.db.Dispositivo
import com.example.android.db.AparatoTipo
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface DeviceApiService {
    @GET("api/Dim_Aparatos")
    suspend fun getDispositivos(@Header("Authorization") token: String): Response<ApiResponse<List<Dispositivo>>>

    @GET("api/AparatoTipos")
    suspend fun getTiposAparato(@Header("Authorization") token: String): Response<ApiResponse<List<AparatoTipo>>>

    @GET("api/Dim_Aparatos/{id}")
    suspend fun getDispositivo(@Header("Authorization") token: String, @Path("id") id: Int): Response<ApiResponse<Dispositivo>>

    @POST("api/Dim_Aparatos")
    suspend fun createDispositivo(@Header("Authorization") token: String, @Body dispositivo: Dispositivo): Response<ApiResponse<Dispositivo>>

    @PUT("api/Dim_Aparatos/{id}")
    suspend fun updateDispositivo(@Header("Authorization") token: String, @Path("id") id: Int, @Body dispositivo: Dispositivo): Response<ResponseBody>

    @DELETE("api/Dim_Aparatos/{id}")
    suspend fun deleteDispositivo(@Header("Authorization") token: String, @Path("id") id: Int): Response<ResponseBody>

    @PUT("api/Dim_Aparatos/{sk_aparato_id}/configuracion-red")
    suspend fun saveConfiguracionRed(
        @Header("Authorization") token: String,
        @Path("sk_aparato_id") aparatoId: Int,
        @Body config: ConfiguracionRedRequest
    ): Response<ApiResponse<ConfiguracionRedResponse>>

    @GET("ws/status/{deviceKey}")
    suspend fun getWsStatus(@Path("deviceKey") deviceKey: String): Response<WsStatusResponse>

    @POST("ws/toggle/{sk_aparato_id}")
    suspend fun toggleAparato(
        @Path("sk_aparato_id") aparatoId: Int,
        @retrofit2.http.Query("estado") estado: Boolean
    ): Response<ResponseBody>
}

data class WsStatusResponse(
    val connected: Boolean
)
