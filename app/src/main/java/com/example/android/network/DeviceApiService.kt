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

    @POST("ws/toggle/{sk_aparato_id}/contacto/{contacto}")
    suspend fun toggleAparatoContacto(
        @Path("sk_aparato_id") aparatoId: Int,
        @Path("contacto") contacto: Int,
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
    ): Response<ApiResponse<List<AparatoMensajeResponse>>>

    @GET("api/Dim_Aparatos/{sk_aparato_id}/consumo")
    suspend fun getConsumoHistorico(
        @Header("Authorization") token: String,
        @Path("sk_aparato_id") aparatoId: Int,
        @retrofit2.http.Query("limit") limit: Int = 20
    ): Response<ApiResponse<List<AparatoConsumoResponse>>>

    @GET("api/Dim_Aparatos/{sk_aparato_id}/consumo/actual")
    suspend fun getConsumoActual(
        @Header("Authorization") token: String,
        @Path("sk_aparato_id") aparatoId: Int
    ): Response<ApiResponse<AparatoConsumoActualResponse>>

    @GET("api/Dim_Aparatos/{sk_aparato_id}/consumo/resumen")
    suspend fun getConsumoResumen(
        @Header("Authorization") token: String,
        @Path("sk_aparato_id") aparatoId: Int,
        @retrofit2.http.Query("granularidad") granularidad: String,
        @retrofit2.http.Query("desde") desde: String?,
        @retrofit2.http.Query("hasta") hasta: String?
    ): Response<ApiResponse<AparatoConsumoResumenResponse>>
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
    @com.google.gson.annotations.SerializedName("estado_encendido_2")
    val estadoEncendido2: Boolean? = null,
    @com.google.gson.annotations.SerializedName("estado_encendido_3")
    val estadoEncendido3: Boolean? = null,
    @com.google.gson.annotations.SerializedName("estado_encendido_4")
    val estadoEncendido4: Boolean? = null,
    @com.google.gson.annotations.SerializedName("conectado")
    val conectado: Boolean,
    @com.google.gson.annotations.SerializedName("fecha_estado_actualizado")
    val fechaEstadoActualizado: String?,
    @com.google.gson.annotations.SerializedName("origen_estado")
    val origenEstado: String?,
    @com.google.gson.annotations.SerializedName("corriente_a")
    val corrienteA: Float? = null,
    @com.google.gson.annotations.SerializedName("potencia_w")
    val potenciaW: Float? = null,
    @com.google.gson.annotations.SerializedName("energia_acumulada_wh")
    val energiaAcumuladaWh: Float? = null
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
    val estadoEncendido: Boolean?,
    @com.google.gson.annotations.SerializedName("estado_encendido_2")
    val estadoEncendido2: Boolean? = null,
    @com.google.gson.annotations.SerializedName("estado_encendido_3")
    val estadoEncendido3: Boolean? = null,
    @com.google.gson.annotations.SerializedName("estado_encendido_4")
    val estadoEncendido4: Boolean? = null,
    val contacto: Int? = null,
    val estado: Boolean? = null
)

data class AparatoConsumoResponse(
    @com.google.gson.annotations.SerializedName("sk_consumo_id")
    val id: Long,
    @com.google.gson.annotations.SerializedName("sk_aparato_id")
    val aparatoId: Int,
    @com.google.gson.annotations.SerializedName("corriente_a")
    val corrienteA: Float,
    @com.google.gson.annotations.SerializedName("potencia_w")
    val potenciaW: Float,
    @com.google.gson.annotations.SerializedName("energia_wh")
    val energiaWh: Float,
    @com.google.gson.annotations.SerializedName("fecha_medicion")
    val fechaMedicion: String
)

data class AparatoConsumoActualResponse(
    @com.google.gson.annotations.SerializedName("corriente_a")
    val corrienteA: Float?,
    @com.google.gson.annotations.SerializedName("potencia_w")
    val potenciaW: Float?,
    @com.google.gson.annotations.SerializedName("energia_acumulada_wh")
    val energiaAcumuladaWh: Float?,
    @com.google.gson.annotations.SerializedName("fecha_medicion_consumo")
    val fechaMedicionConsumo: String?
)

data class AparatoConsumoPuntoResponse(
    @com.google.gson.annotations.SerializedName("periodo")
    val periodo: String,
    @com.google.gson.annotations.SerializedName("potencia_promedio_w")
    val potenciaPromedioW: Float,
    @com.google.gson.annotations.SerializedName("corriente_promedio_a")
    val corrientePromedioA: Float,
    @com.google.gson.annotations.SerializedName("energia_consumida_wh")
    val energiaConsumidaWh: Float
)

data class AparatoConsumoResumenResponse(
    @com.google.gson.annotations.SerializedName("granularidad")
    val granularidad: String,
    @com.google.gson.annotations.SerializedName("desde")
    val desde: String,
    @com.google.gson.annotations.SerializedName("hasta")
    val hasta: String,
    @com.google.gson.annotations.SerializedName("puntos")
    val puntos: List<AparatoConsumoPuntoResponse>
)
