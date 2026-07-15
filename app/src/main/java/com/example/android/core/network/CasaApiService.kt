package com.example.android.core.network

import com.example.android.core.db.Casa
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface CasaApiService {
    @GET("api/Casas")
    suspend fun getCasas(@Header("Authorization") token: String): Response<ApiResponse<List<Casa>>>

    @POST("api/Casas")
    suspend fun createCasa(@Header("Authorization") token: String, @Body casa: Casa): Response<ApiResponse<Casa>>

    @PUT("api/Casas/{id}")
    suspend fun updateCasa(@Header("Authorization") token: String, @Path("id") id: Int, @Body casa: Casa): Response<ApiResponse<Any>>

    @DELETE("api/Casas/{id}")
    suspend fun deleteCasa(@Header("Authorization") token: String, @Path("id") id: Int): Response<ApiResponse<Any>>
}
