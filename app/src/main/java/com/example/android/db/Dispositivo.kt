package com.example.android.db

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

    @SerializedName("conectado_red")
    @androidx.room.ColumnInfo(name = "conectado_red")
    val conectadoRed: Boolean? = null,

    @SerializedName("fecha_estado_actualizado")
    @androidx.room.ColumnInfo(name = "fecha_estado_actualizado")
    val fechaEstadoActualizado: String? = null
)
