package com.example.android.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "gestos",
    foreignKeys = [
        ForeignKey(
            entity = Dispositivo::class,
            parentColumns = ["id"],
            childColumns = ["aparatoId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class Gesto(
    @PrimaryKey
    @SerializedName("sk_gesto_id")
    val id: Int,

    @SerializedName("bk_gesto_id")
    val bkId: Int,

    @SerializedName("nombre_gesto")
    val nombre: String?,

    @SerializedName("identificador_ia")
    val identificadorIa: Int,

    @SerializedName("nivel_confianza_minimo")
    val nivelConfianzaMinimo: Double,

    @SerializedName("tipo_disparador_nombre")
    val tipoDisparadorNombre: String?,

    @SerializedName("sk_aparato_id")
    val aparatoId: Int?,

    @SerializedName("frase_voz_activadora")
    val fraseVozActivadora: String? = null,

    @androidx.room.Ignore
    @SerializedName("pasos")
    val pasos: List<GestoPaso>? = null
) {
    constructor(id: Int, bkId: Int, nombre: String?, identificadorIa: Int, nivelConfianzaMinimo: Double, tipoDisparadorNombre: String?, aparatoId: Int?, fraseVozActivadora: String?) : 
        this(id, bkId, nombre, identificadorIa, nivelConfianzaMinimo, tipoDisparadorNombre, aparatoId, fraseVozActivadora, null)
}
