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
    suspend fun updateDispositivo(@Header("Authorization") token: String, @Path("id") id: Int, @Body dispositivo: Dispositivo): Response<ApiResponse<Any>>

    @DELETE("api/Dim_Aparatos/{id}")
    suspend fun deleteDispositivo(@Header("Authorization") token: String, @Path("id") id: Int): Response<ApiResponse<Any>>

    @PUT("api/Dim_Aparatos/{sk_aparato_id}/configuracion-red")
    suspend fun saveConfiguracionRed(
        @Header("Authorization") token: String,
        @Path("sk_aparato_id") aparatoId: Int,
        @Body config: ConfiguracionRedRequest
    ): Response<ApiResponse<ConfiguracionRedResponse>>

    @GET("ws/accion")
    suspend fun enviarComandoWebSocket(
        @retrofit2.http.Query("comando") comando: String,
        @retrofit2.http.Query("deviceKey") deviceKey: String
    ): Response<ResponseBody>

    @GET("ws/status/{deviceKey}")
    suspend fun getWsStatus(@Path("deviceKey") deviceKey: String): Response<WsStatusResponse>

    @POST("ws/toggle/{sk_aparato_id}")
    suspend fun toggleAparato(
        @Path("sk_aparato_id") aparatoId: Int,
        @retrofit2.http.Query("estado") estado: Boolean
    ): Response<ToggleAparatoResponse>

    @GET("ws/status/all")
    suspend fun getWsStatusAll(): Response<WsStatusAllResponse>

    @GET("ws/state/{sk_aparato_id}")
    suspend fun getAparatoEstado(@Path("sk_aparato_id") aparatoId: Int): Response<AparatoEstadoResponse>

    @GET("api/Dim_Aparatos/{sk_aparato_id}/mensajes")
    suspend fun getMensajesSocket(
        @Header("Authorization") token: String,
        @Path("sk_aparato_id") aparatoId: Int,
        @retrofit2.http.Query("limit") limit: Int = 20
    ): Response<List<AparatoMensajeResponse>>
}

data class WsStatusResponse(
    val connected: Boolean,
    @com.google.gson.annotations.SerializedName("estado_encendido")
    val estadoEncendido: Boolean? = null
)

data class WsStatusAllResponse(
    val connectedDevices: List<String>
)

data class AparatoEstadoResponse(
    @com.google.gson.annotations.SerializedName("sk_aparato_id")
    val aparatoId: Int,
    @com.google.gson.annotations.SerializedName("device_key")
    val deviceKey: String?,
    @com.google.gson.annotations.SerializedName("estado_encendido")
    val estadoEncendido: Boolean?,
    @com.google.gson.annotations.SerializedName("conectado")
    val conectado: Boolean,
    @com.google.gson.annotations.SerializedName("fecha_estado_actualizado")
    val fechaEstadoActualizado: String?,
    @com.google.gson.annotations.SerializedName("origen_estado")
    val origenEstado: String?
)

data class AparatoMensajeResponse(
    @com.google.gson.annotations.SerializedName("sk_mensaje_id")
    val id: Long,
    @com.google.gson.annotations.SerializedName("sk_aparato_id")
    val aparatoId: Int,
    @com.google.gson.annotations.SerializedName("direccion")
    val direccion: String,
    @com.google.gson.annotations.SerializedName("payload_json")
    val payloadJson: String,
    @com.google.gson.annotations.SerializedName("comando")
    val comando: String?,
    @com.google.gson.annotations.SerializedName("procesado")
    val procesado: Boolean,
    @com.google.gson.annotations.SerializedName("fecha_creacion")
    val fechaCreacion: String
)

data class ToggleAparatoResponse(
    val success: Boolean,
    val comando: String?,
    @com.google.gson.annotations.SerializedName("estado_encendido")
    val estadoEncendido: Boolean?
)
