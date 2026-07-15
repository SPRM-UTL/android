package com.example.android.db

import androidx.room.ColumnInfo
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
            childColumns = ["sk_aparato_id"], // 👈 CORREGIDO: Debe apuntar al nombre de la columna en la BD local
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class Gesto(
    @PrimaryKey
    @SerializedName("sk_gesto_id")
    @ColumnInfo(name = "sk_gesto_id")
    val id: Int,

    @SerializedName("bk_gesto_id")
    @ColumnInfo(name = "bk_gesto_id")
    val bkId: Int,

    @SerializedName("nombre_gesto")
    @ColumnInfo(name = "nombre_gesto")
    val nombre: String?,

    @SerializedName("identificador_ia")
    @ColumnInfo(name = "identificador_ia")
    val identificadorIa: Int,

    @SerializedName("nivel_confianza_minimo")
    @ColumnInfo(name = "nivel_confianza_minimo")
    val nivelConfianzaMinimo: Double,

    @SerializedName("tipo_disparador_nombre")
    @ColumnInfo(name = "tipo_disparador_nombre")
    val tipoDisparadorNombre: String?,

    @SerializedName("sk_aparato_id")
    @ColumnInfo(name = "sk_aparato_id") // 🌟 Este nombre debe ser idéntico al childColumns de arriba
    val aparatoId: Int?,

    @SerializedName("icono")
    @ColumnInfo(name = "icono")
    val icono: String? = "lucide_star",

    @SerializedName("frase_voz_activadora")
    @ColumnInfo(name = "frase_voz_activadora")
    val fraseVozActivadora: String? = null,

    @androidx.room.Ignore
    @SerializedName("pasos")
    val pasos: List<GestoPaso>? = null
) {
    constructor(
        id: Int,
        bkId: Int,
        nombre: String?,
        identificadorIa: Int,
        nivelConfianzaMinimo: Double,
        tipoDisparadorNombre: String?,
        aparatoId: Int?,
        icono: String?,
        fraseVozActivadora: String?
    ) : this(id, bkId, nombre, identificadorIa, nivelConfianzaMinimo, tipoDisparadorNombre, aparatoId, icono, fraseVozActivadora, null)
}