package com.hora.muthal.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hora.muthal.databinding.SheetJoinInstitutionBinding

/** Prompts for a 6-character join code; [onSubmit] resolves it (SPEC §2). */
class JoinInstitutionBottomSheet(
    private val prefillCode: String? = null,
    private val onSubmit: (String) -> Unit,
) : BottomSheetDialogFragment() {

    private var _b: SheetJoinInstitutionBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        val b = SheetJoinInstitutionBinding.inflate(inflater, container, false)
        _b = b
        prefillCode?.let { b.inputCode.setText(it) }
        b.btnResolveJoin.setOnClickListener {
            val code = b.inputCode.text?.toString()?.trim()?.uppercase().orEmpty()
            if (code.length < 6) {
                b.inputCode.error = "Enter the 6-character code"
                return@setOnClickListener
            }
            onSubmit(code)
            dismiss()
        }
        return b.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
