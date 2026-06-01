package com.example.android

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

class MenuBottomSheetDialog(
    private val onProfileClick: () -> Unit,
    private val onSettingsClick: () -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.item_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialButton>(R.id.profile).setOnClickListener {
            onProfileClick()
            dismiss()        }

        view.findViewById<MaterialButton>(R.id.settings).setOnClickListener {
            onSettingsClick()
            dismiss()
        }


    }
}