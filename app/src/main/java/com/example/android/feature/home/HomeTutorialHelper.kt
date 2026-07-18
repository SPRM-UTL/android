package com.example.android.feature.home
import com.example.android.feature.home.HomeTutorialHelper

import android.content.Context
import android.graphics.Typeface
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.example.android.R
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence

class HomeTutorialHelper {

    fun mostrarTutorial(
        fragment: Fragment,
        vistaRaiz: View,
        btnConfigurarRed: View,
        rvDispositivos: RecyclerView
    ) {
        val sharedPref = fragment.requireContext().getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
        val tutorialVisto = sharedPref.getBoolean("tutorial_visto", false)

        if (tutorialVisto) return

        vistaRaiz.postDelayed({
            if (!fragment.isAdded) return@postDelayed

            val typeFace = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

            val targetRed = TapTarget.forView(
                btnConfigurarRed,
                "1. Conecta tu Hogar",
                "Lo primero es configurar y vincular tu hardware controlador (ESP32) para que la app pueda comunicarse con tu casa."
            )
                .outerCircleColor(R.color.teal_primary)
                .outerCircleAlpha(0.96f)
                .targetCircleColor(R.color.white)
                .titleTextSize(22)
                .titleTextColor(R.color.white)
                .descriptionTextSize(14)
                .descriptionTextColor(R.color.white)
                .textColor(R.color.white)
                .textTypeface(typeFace)
                .dimColor(R.color.black)
                .drawShadow(true)
                .cancelable(false)
                .tintTarget(true)
                .transparentTarget(true)
                .targetRadius(30)

            val targetDispositivos = TapTarget.forView(
                vistaRaiz.findViewById(R.id.tvTituloDispositivos) ?: rvDispositivos,
                "2. Añade y Controla",
                "Una vez conectado, agrega tus dispositivos (focos, ventiladores, etc.) aquí para controlarlos manualmente con un solo toque."
            )
                .outerCircleColor(R.color.teal_primary)
                .outerCircleAlpha(0.96f)
                .targetCircleColor(R.color.white)
                .titleTextSize(22)
                .titleTextColor(R.color.white)
                .descriptionTextSize(14)
                .descriptionTextColor(R.color.white)
                .textColor(R.color.white)
                .textTypeface(typeFace)
                .dimColor(R.color.black)
                .drawShadow(true)
                .cancelable(false)
                .tintTarget(true)
                .transparentTarget(true)
                .targetRadius(60)

            val targetIA = TapTarget.forView(
                fragment.requireActivity().findViewById(R.id.bottom_bar_container),
                "3. Inteligencia Artificial",
                "Abre el menú central para activar la cámara, crear secuencias de gestos corporales y programar rutinas automatizadas."
            )
                .outerCircleColor(R.color.teal_primary)
                .outerCircleAlpha(0.96f)
                .targetCircleColor(R.color.white)
                .titleTextSize(22)
                .titleTextColor(R.color.white)
                .descriptionTextSize(14)
                .descriptionTextColor(R.color.white)
                .textColor(R.color.white)
                .textTypeface(typeFace)
                .dimColor(R.color.black)
                .drawShadow(true)
                .cancelable(false)
                .tintTarget(true)
                .transparentTarget(true)
                .targetRadius(70)

            val targetPerfil = TapTarget.forView(
                vistaRaiz.findViewById(R.id.profileCircle),
                "4. Tu Perfil",
                "Accede aquí para ajustar tus preferencias, habilitar la huella dactilar y cerrar tu sesión."
            )
                .outerCircleColor(R.color.teal_primary)
                .outerCircleAlpha(0.96f)
                .targetCircleColor(R.color.white)
                .titleTextSize(22)
                .titleTextColor(R.color.white)
                .descriptionTextSize(14)
                .descriptionTextColor(R.color.white)
                .textColor(R.color.white)
                .textTypeface(typeFace)
                .dimColor(R.color.black)
                .drawShadow(true)
                .cancelable(false)
                .tintTarget(false)
                .transparentTarget(true)
                .targetRadius(40)

            TapTargetSequence(fragment.requireActivity())
                .targets(targetRed, targetDispositivos, targetIA, targetPerfil)
                .listener(object : TapTargetSequence.Listener {
                    override fun onSequenceFinish() {
                        sharedPref.edit().putBoolean("tutorial_visto", true).apply()
                    }

                    override fun onSequenceStep(lastTarget: TapTarget?, targetClicked: Boolean) {}

                    override fun onSequenceCanceled(lastTarget: TapTarget?) {
                        sharedPref.edit().putBoolean("tutorial_visto", true).apply()
                    }
                })
                .start()
        }, 800)
    }
}
