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
    var accionVinculada: String? = null
)

object SecuenciaConfigManager {
    private const val PREFS_NAME_BASE = "sequence_prefs_v2"
    private const val KEY_COMBOS = "combos_array"

    // Lee el userId de la sesión actual (guardado en MainActivity al hacer login).
    // Si no hay sesión activa, usamos -1 como bucket separado.
    private fun getCurrentUserId(context: Context): Int {
        val sesionPrefs = context.getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        return sesionPrefs.getInt("userId", -1)
    }

    // Cada usuario obtiene su propio archivo de SharedPreferences,
    // por ejemplo: "sequence_prefs_v2_user_5", "sequence_prefs_v2_user_12", etc.
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
}