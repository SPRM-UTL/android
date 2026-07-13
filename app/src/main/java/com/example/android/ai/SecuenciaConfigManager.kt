package com.example.android.ai
import com.example.android.R

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

import java.util.UUID

data class Combo(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "Nuevo Combo",
    var activador: PasoSecuencia? = null,
    var pasos: MutableList<PasoSecuencia> = mutableListOf(),
    var accionVinculada: String? = null,
    var aparatoId: Int? = null,
    var accionEncendido: Boolean? = null,
    var backendGestoId: Int? = null, // ID del registro 'gesto' en el backend (null = aún no sincronizado)
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

    private fun getPrefsName(context: Context): String {
        val userId = getCurrentUserId(context)
        return "${PREFS_NAME_BASE}_user_$userId"
    }

    fun saveCombos(context: Context, combos: List<Combo>) {
        val prefs = context.getSharedPreferences(getPrefsName(context), Context.MODE_PRIVATE)
        val editor = prefs.edit()

        val combosArray = JSONArray()
        for (combo in combos) {
            val comboObj = JSONObject()
            comboObj.put("id", combo.id)
            comboObj.put("name", combo.name)
            if (combo.activador != null) comboObj.put("activador", stepToJson(combo.activador!!))
            if (combo.accionVinculada != null) comboObj.put("accionVinculada", combo.accionVinculada)
            if (combo.aparatoId != null) comboObj.put("aparatoId", combo.aparatoId)
            if (combo.accionEncendido != null) comboObj.put("accionEncendido", combo.accionEncendido)
            if (combo.backendGestoId != null) comboObj.put("backendGestoId", combo.backendGestoId)
            if (combo.icono != null) comboObj.put("icono", combo.icono)
            if (!combo.fraseVozActivadora.isNullOrBlank()) comboObj.put("fraseVozActivadora", combo.fraseVozActivadora)

            val pasosArray = JSONArray()
            for (step in combo.pasos) {
                pasosArray.put(stepToJson(step))
            }
            comboObj.put("pasos", pasosArray)

            combosArray.put(comboObj)
        }
        editor.putString(KEY_COMBOS, combosArray.toString())
        editor.apply()
    }

    fun loadCombos(context: Context): List<Combo> {
        val prefs = context.getSharedPreferences(getPrefsName(context), Context.MODE_PRIVATE)
        val combosStr = prefs.getString(KEY_COMBOS, null)

        val defaultCombo = Combo(
            id = "default-combo",
            name = "Ataque Base",
            activador = PasoSecuencia("TE AMO ILY", ManoObjetivo.ANY, 15),
            pasos = mutableListOf(
                PasoSecuencia("L", ManoObjetivo.ANY, 10),
                PasoSecuencia("ROCK", ManoObjetivo.ANY, 10),
                PasoSecuencia("L", ManoObjetivo.ANY, 10)
            ),
            accionVinculada = "Encender Luces Sala"
        )

        if (combosStr == null) {
            return listOf(defaultCombo)
        }

        try {
            val combosArray = JSONArray(combosStr)
            val combosList = mutableListOf<Combo>()
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
                combosList.add(combo)
            }
            return combosList
        } catch (e: Exception) {
            e.printStackTrace()
            return listOf(defaultCombo)
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

    private fun comboToGesto(combo: Combo): com.example.android.db.Gesto {
        val pasosList = mutableListOf<com.example.android.db.GestoPaso>()
        var order = 1
        if (combo.activador != null) {
            pasosList.add(com.example.android.db.GestoPaso(orden = order++, esActivador = true, nombreGesto = combo.activador!!.nombreGesto, manoObjetivo = combo.activador!!.manoObjetivo.name, cuadrosRequeridos = combo.activador!!.cuadrosRequeridos))
        }
        combo.pasos.forEach { paso ->
            pasosList.add(com.example.android.db.GestoPaso(orden = order++, esActivador = false, nombreGesto = paso.nombreGesto, manoObjetivo = paso.manoObjetivo.name, cuadrosRequeridos = paso.cuadrosRequeridos))
        }
        
        return com.example.android.db.Gesto(
            id = combo.backendGestoId ?: 0,
            bkId = 0,
            nombre = combo.name,
            identificadorIa = 0,
            nivelConfianzaMinimo = 0.5,
            tipoDisparadorNombre = if (combo.fraseVozActivadora != null && combo.activador == null && combo.pasos.isEmpty()) "VOZ" else "COMBO",
            aparatoId = combo.aparatoId,
            pasos = pasosList,
            fraseVozActivadora = combo.fraseVozActivadora
        )
    }

    suspend fun pushComboToBackend(context: Context, combo: Combo): Boolean {
        val prefs = context.getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val token = prefs.getString("apiToken", "") ?: ""
        if (token.isEmpty()) return false

        try {
            val gesto = comboToGesto(combo)
            val service = com.example.android.network.RetrofitClient.gestureService
            
            if (combo.backendGestoId == null || combo.backendGestoId == 0) {
                // Create
                val response = service.createGesto("Bearer $token", gesto)
                if (response.isSuccessful) {
                    val createdGesto = response.body()?.data
                    if (createdGesto != null) {
                        combo.backendGestoId = createdGesto.id
                        return true
                    }
                }
            } else {
                // Update
                val response = service.updateGesto("Bearer $token", combo.backendGestoId!!, gesto)
                return response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}