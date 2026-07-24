package com.example.android.feature.ai.domain.manager

import com.example.android.feature.ai.domain.analyzer.PasoSecuencia
import com.example.android.feature.ai.domain.analyzer.ManoObjetivo
import android.content.Context
import com.example.android.R
import com.example.android.core.db.models.ComboEntity
import com.example.android.core.db.models.Gesto
import com.example.android.core.db.models.GestoPaso
import com.example.android.core.db.init.AppDatabase
import com.example.android.core.network.client.RetrofitClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.runBlocking

data class Combo(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "Nuevo Combo",
    var activador: PasoSecuencia? = null,
    var pasos: MutableList<PasoSecuencia> = mutableListOf(),
    var accionVinculada: String? = null,
    var aparatoId: Int? = null,
    var accionEncendido: Boolean? = null,
    var contactoOutlet: Int? = null,
    var backendGestoId: Int? = null,
    var icono: String? = "lucide_star",
    var fraseVozActivadora: String? = null
)

object SecuenciaConfigManager {
    private const val PREFS_NAME_BASE = "sequence_prefs_v2"
    private const val KEY_COMBOS = "combos_array"

    private fun getCurrentUserId(context: Context): Int {
        val sesionPrefs = context.getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        return sesionPrefs.getInt("userId", -1)
    }

    private suspend fun getComboDao(context: Context): com.example.android.core.db.dao.ComboDao {
        return AppDatabase.getDatabase(context).comboDao()
    }

    suspend fun saveCombos(context: Context, combos: List<Combo>) {
        val userId = getCurrentUserId(context)
        val dao = getComboDao(context)

        dao.deleteAllCombosByUserId(userId)

        val entities = combos.map { comboToEntity(it, userId) }
        dao.insertAll(entities)

        migrateFromPrefsIfNeeded(context, userId)
    }

    suspend fun loadCombos(context: Context): List<Combo> {
        val userId = getCurrentUserId(context)
        val dao = getComboDao(context)

        val entities = dao.getCombosByUserId(userId)

        return entities.map { entityToCombo(it) }
    }

    suspend fun saveCombo(context: Context, combo: Combo) {
        val userId = getCurrentUserId(context)
        val dao = getComboDao(context)
        val entity = comboToEntity(combo, userId)
        dao.insertCombo(entity)
    }

    suspend fun deleteCombo(context: Context, comboId: String) {
        val dao = getComboDao(context)
        dao.deleteComboById(comboId)
    }

    suspend fun deleteComboFull(context: Context, combo: Combo): Boolean {
        // 1. Borrado en la BD Local (Room / ComboEntity)
        deleteCombo(context, combo.id)

        // 2. Si tiene ID asignado por el backend, eliminar en la API
        val backendId = combo.backendGestoId
        if (backendId != null && backendId > 0) {
            val prefs = context.getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
            val token = prefs.getString("apiToken", "") ?: ""
            if (token.isNotEmpty()) {
                try {
                    val response = RetrofitClient.gestureService.deleteGesto("Bearer $token", backendId)
                    if (response.isSuccessful) {
                        // Limpiar también de la tabla 'gestos'
                        val db = AppDatabase.getDatabase(context)
                        db.gestoDao().deleteGestoById(backendId)
                        return true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return true
    }

    private fun comboToEntity(combo: Combo, userId: Int): ComboEntity {
        return ComboEntity(
            id = combo.id,
            name = combo.name.ifBlank { "Nuevo Combo" },
            activadorJson = combo.activador?.let { stepToJson(it).toString() },
            pasosJson = JSONArray().apply {
                combo.pasos.forEach { put(stepToJson(it)) }
            }.toString(),
            accionVinculada = combo.accionVinculada,
            aparatoId = combo.aparatoId,
            accionEncendido = combo.accionEncendido,
            contactoOutlet = combo.contactoOutlet,
            backendGestoId = combo.backendGestoId,
            icono = combo.icono,
            fraseVozActivadora = combo.fraseVozActivadora,
            userId = userId
        )
    }

    private fun entityToCombo(entity: ComboEntity): Combo {
        val combo = Combo(
            id = entity.id,
            name = entity.name.ifBlank { "Nuevo Combo" },
            accionVinculada = entity.accionVinculada,
            aparatoId = entity.aparatoId,
            accionEncendido = entity.accionEncendido,
            contactoOutlet = entity.contactoOutlet,
            backendGestoId = entity.backendGestoId,
            icono = entity.icono,
            fraseVozActivadora = entity.fraseVozActivadora
        )

        entity.activadorJson?.let {
            try {
                combo.activador = stepFromJson(JSONObject(it))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            val pasosArray = JSONArray(entity.pasosJson)
            for (j in 0 until pasosArray.length()) {
                combo.pasos.add(stepFromJson(pasosArray.getJSONObject(j)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return combo
    }

    private fun migrateFromPrefsIfNeeded(context: Context, userId: Int) {
        val prefs = context.getSharedPreferences("${PREFS_NAME_BASE}_user_$userId", Context.MODE_PRIVATE)
        val combosStr = prefs.getString(KEY_COMBOS, null) ?: return

        try {
            val combosArray = JSONArray(combosStr)
            val dao = runBlocking { getComboDao(context) }

            for (i in 0 until combosArray.length()) {
                val comboObj = combosArray.getJSONObject(i)
                val combo = Combo(
                    id = comboObj.optString("id", UUID.randomUUID().toString()),
                    name = comboObj.optString("name", "Combo Desconocido")
                )
                if (comboObj.has("activador")) {
                    combo.activador = stepFromJson(comboObj.getJSONObject("activador"))
                }
                if (comboObj.has("accionVinculada")) {
                    combo.accionVinculada = comboObj.getString("accionVinculada")
                }
                if (comboObj.has("aparatoId")) {
                    combo.aparatoId = comboObj.optInt("aparatoId").takeIf { it > 0 }
                }
                if (comboObj.has("accionEncendido")) {
                    combo.accionEncendido = comboObj.getBoolean("accionEncendido")
                }
                if (comboObj.has("contactoOutlet")) {
                    combo.contactoOutlet = comboObj.optInt("contactoOutlet").takeIf { it > 0 }
                }
                if (comboObj.has("backendGestoId")) {
                    combo.backendGestoId = comboObj.getInt("backendGestoId")
                }
                if (comboObj.has("icono")) {
                    combo.icono = comboObj.getString("icono")
                }
                if (comboObj.has("fraseVozActivadora")) {
                    combo.fraseVozActivadora = comboObj.getString("fraseVozActivadora")
                }
                if (comboObj.has("pasos")) {
                    val pasosArray = comboObj.getJSONArray("pasos")
                    for (j in 0 until pasosArray.length()) {
                        combo.pasos.add(stepFromJson(pasosArray.getJSONObject(j)))
                    }
                }

                val entity = comboToEntity(combo, userId)
                runBlocking { dao.insertCombo(entity) }
            }

            prefs.edit().remove(KEY_COMBOS).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stepToJson(step: PasoSecuencia): JSONObject {
        val obj = JSONObject()
        obj.put("nombreGesto", step.nombreGesto)
        obj.put("manoObjetivo", step.manoObjetivo.name)
        obj.put("cuadrosRequeridos", step.cuadrosRequeridos)
        return obj
    }

    private fun stepFromJson(obj: JSONObject): PasoSecuencia {
        return PasoSecuencia(
            nombreGesto = obj.getString("nombreGesto"),
            manoObjetivo = ManoObjetivo.valueOf(obj.getString("manoObjetivo")),
            cuadrosRequeridos = obj.getInt("cuadrosRequeridos")
        )
    }

    fun comboToGesto(combo: Combo): Gesto {
        val pasosList = mutableListOf<GestoPaso>()
        var order = 1
        if (combo.activador != null) {
            pasosList.add(
                GestoPaso(
                    orden = order++,
                    esActivador = true,
                    nombreGesto = combo.activador!!.nombreGesto,
                    manoObjetivo = combo.activador!!.manoObjetivo.name,
                    cuadrosRequeridos = combo.activador!!.cuadrosRequeridos
                )
            )
        }
        combo.pasos.forEach { paso ->
            pasosList.add(
                GestoPaso(
                    orden = order++,
                    esActivador = false,
                    nombreGesto = paso.nombreGesto,
                    manoObjetivo = paso.manoObjetivo.name,
                    cuadrosRequeridos = paso.cuadrosRequeridos
                )
            )
        }

        return Gesto(
            id = combo.backendGestoId ?: 0,
            bkId = 0,
            nombre = combo.name.ifBlank { "Nuevo Combo" },
            identificadorIa = 0,
            nivelConfianzaMinimo = 0.5,
            tipoDisparadorNombre = if (combo.fraseVozActivadora != null && combo.activador == null && combo.pasos.isEmpty()) "VOZ" else "COMBO",
            aparatoId = combo.aparatoId,
            contactoOutlet = combo.contactoOutlet,
            icono = combo.icono,
            pasos = pasosList,
            fraseVozActivadora = combo.fraseVozActivadora
        )
    }

    fun gestoToCombo(gesto: Gesto): Combo {
        val combo = Combo(
            id = UUID.randomUUID().toString(),
            name = gesto.nombre.orEmpty().ifBlank { "Nuevo Combo" },
            aparatoId = gesto.aparatoId,
            contactoOutlet = gesto.contactoOutlet,
            backendGestoId = gesto.id,
            icono = gesto.icono ?: "lucide_star",
            fraseVozActivadora = gesto.fraseVozActivadora
        )

        gesto.pasos?.sortedBy { it.orden }?.forEach { paso ->
            val pasoSecuencia = PasoSecuencia(
                nombreGesto = paso.nombreGesto,
                manoObjetivo = try { ManoObjetivo.valueOf(paso.manoObjetivo) } catch (e: Exception) { ManoObjetivo.ANY },
                cuadrosRequeridos = paso.cuadrosRequeridos
            )
            if (paso.esActivador) {
                combo.activador = pasoSecuencia
            } else {
                combo.pasos.add(pasoSecuencia)
            }
        }

        if (gesto.aparatoId != null) {
            val canal = gesto.contactoOutlet ?: 1
            combo.accionVinculada = if (canal > 1) {
                "Velocidad $canal · Dispositivo #${gesto.aparatoId}"
            } else {
                "Encender · Dispositivo #${gesto.aparatoId}"
            }
            combo.accionEncendido = true
        }

        return combo
    }

    suspend fun pushComboToBackend(context: Context, combo: Combo): Boolean {
        val prefs = context.getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val token = prefs.getString("apiToken", "") ?: ""
        if (token.isEmpty()) return false

        try {
            val gesto = comboToGesto(combo)
            val service = RetrofitClient.gestureService

            if (combo.backendGestoId == null || combo.backendGestoId == 0) {
                val response = service.createGesto("Bearer $token", gesto)
                if (response.isSuccessful) {
                    val createdGesto = response.body()?.data
                    if (createdGesto != null) {
                        combo.backendGestoId = createdGesto.id
                        saveCombo(context, combo)
                        return true
                    }
                }
            } else {
                val response = service.updateGesto("Bearer $token", combo.backendGestoId!!, gesto)
                return response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}