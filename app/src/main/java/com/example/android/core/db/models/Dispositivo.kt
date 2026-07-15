package com.example.android.core.db.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "dispositivos")
data class Dispositivo(
    @PrimaryKey
    @SerializedName("sk_aparato_id")
    val id: Int,

    @SerializedName("nombre_aparato")
    val nombre: String?,

    @SerializedName("tipo_aparato")
    val tipo: String?,

    @SerializedName("accion_nombre")
    val accion: String?,

    @SerializedName("comando_bluetooth")
    val comandoBluetooth: String?,

    @SerializedName("icono")
    val icono: String?,

    @SerializedName("metodo_vinculacion")
    @androidx.room.ColumnInfo(name = "metodo_vinculacion")
    val metodoVinculacion: String? = null,

    @SerializedName("mac_bluetooth")
    @androidx.room.ColumnInfo(name = "mac_bluetooth")
    val macBluetooth: String?,

    @SerializedName("nombre_bluetooth")
    @androidx.room.ColumnInfo(name = "nombre_bluetooth")
    val nombreBluetooth: String?,

    @SerializedName("fecha_sincronizacion")
    @androidx.room.ColumnInfo(name = "fecha_sincronizacion")
    val fechaSincronizacion: String?,

    @SerializedName("sk_habitacion_id")
    @androidx.room.ColumnInfo(name = "sk_habitacion_id")
    val skHabitacionId: Int? = null,

    @SerializedName("nombre_habitacion")
    @androidx.room.ColumnInfo(name = "nombre_habitacion")
    val nombreHabitacion: String? = null,

    @SerializedName("estado_encendido")
    @androidx.room.ColumnInfo(name = "estado_encendido")
    val estadoEncendido: Boolean? = null,

    @SerializedName("estado_encendido_2")
    @androidx.room.ColumnInfo(name = "estado_encendido_2")
    val estadoEncendido2: Boolean? = null,

    @SerializedName("estado_encendido_3")
    @androidx.room.ColumnInfo(name = "estado_encendido_3")
    val estadoEncendido3: Boolean? = null,

    @SerializedName("estado_encendido_4")
    @androidx.room.ColumnInfo(name = "estado_encendido_4")
    val estadoEncendido4: Boolean? = null,

    @SerializedName("conectado_red")
    @androidx.room.ColumnInfo(name = "conectado_red")
    val conectadoRed: Boolean? = null,

    @SerializedName("ip_address")
    @androidx.room.ColumnInfo(name = "ip_address")
    val ipAddress: String? = null,

    @SerializedName("fecha_estado_actualizado")
    @androidx.room.ColumnInfo(name = "fecha_estado_actualizado")
    val fechaEstadoActualizado: String? = null,

    @SerializedName("corriente_a")
    @androidx.room.ColumnInfo(name = "corriente_a")
    val corrienteA: Float? = null,

    @SerializedName("potencia_w")
    @androidx.room.ColumnInfo(name = "potencia_w")
    val potenciaW: Float? = null,

    @SerializedName("energia_acumulada_wh")
    @androidx.room.ColumnInfo(name = "energia_acumulada_wh")
    val energiaAcumuladaWh: Float? = null,

    @SerializedName("fecha_medicion_consumo")
    @androidx.room.ColumnInfo(name = "fecha_medicion_consumo")
    val fechaMedicionConsumo: String? = null
)
