package com.example.android.core.db.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
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
    ],
    indices = [Index("aparatoId")]
)
data class Gesto(
    @PrimaryKey
    @SerializedName("sk_gesto_id", alternate = ["id"])
    val id: Int = 0,

    @SerializedName("bk_gesto_id")
    val bkId: Int = 0,

    @SerializedName("nombre_gesto", alternate = ["nombre"])
    val nombre: String? = null,

    @SerializedName("identificador_ia")
    val identificadorIa: Int = 0,

    @SerializedName("nivel_confianza_minimo")
    val nivelConfianzaMinimo: Double = 0.5,

    @SerializedName("tipo_disparador_nombre")
    val tipoDisparadorNombre: String? = null,

    @SerializedName("sk_aparato_id", alternate = ["aparato_id"])
    val aparatoId: Int? = null,

    @SerializedName("contacto_outlet", alternate = ["contacto_outlet_id", "outlet"])
    val contactoOutlet: Int? = null,

    @SerializedName("icono")
    val icono: String? = "lucide_star",

    @SerializedName("frase_voz_activadora")
    val fraseVozActivadora: String? = null
) {
    @Ignore
    @SerializedName("pasos")
    var pasos: List<GestoPaso>? = null

    constructor(
        id: Int = 0,
        bkId: Int = 0,
        nombre: String? = null,
        identificadorIa: Int = 0,
        nivelConfianzaMinimo: Double = 0.5,
        tipoDisparadorNombre: String? = null,
        aparatoId: Int? = null,
        contactoOutlet: Int? = null,
        icono: String? = "lucide_star",
        fraseVozActivadora: String? = null,
        pasos: List<GestoPaso>? = null
    ) : this(
        id,
        bkId,
        nombre,
        identificadorIa,
        nivelConfianzaMinimo,
        tipoDisparadorNombre,
        aparatoId,
        contactoOutlet,
        icono,
        fraseVozActivadora
    ) {
        this.pasos = pasos
    }
}