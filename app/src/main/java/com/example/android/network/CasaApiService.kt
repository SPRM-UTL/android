package com.example.android.network

import com.example.android.db.Casa
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
    suspend fun getCasas(@Header("Authorization") token: String): Response<List<Casa>>

    @POST("api/Casas")
    suspend fun createCasa(@Header("Authorization") token: String, @Body casa: Casa): Response<Casa>

    @PUT("api/Casas/{id}")
    suspend fun updateCasa(@Header("Authorization") token: String, @Path("id") id: Int, @Body casa: Casa): Response<ResponseBody>

    @DELETE("api/Casas/{id}")
    suspend fun deleteCasa(@Header("Authorization") token: String, @Path("id") id: Int): Response<ResponseBody>
}
