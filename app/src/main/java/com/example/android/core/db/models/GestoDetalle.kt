package com.example.android.core.db.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "gesto_detalle",
    foreignKeys = [
        ForeignKey(
            entity = Gesto::class,
            parentColumns = ["id"],
            childColumns = ["gestoId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class GestoDetalle(
    @PrimaryKey(autoGenerate = true)
    @SerializedName("sk_gesto_detail_id") val id: Int = 0,
    @SerializedName("sk_gesto_id") val gestoId: Int,
    @SerializedName("duracion_segundos") val duracionSegundos: Int,
    @SerializedName("iluminacion_recomendada") val iluminacionRecomendada: String?,
    @SerializedName("distancia_recomendada") val distanciaRecomendada: String?
)
