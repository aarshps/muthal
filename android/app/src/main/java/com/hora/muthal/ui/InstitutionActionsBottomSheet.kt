package com.hora.muthal.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hora.muthal.databinding.BottomSheetInstitutionActionsBinding

/** Role-gated action list for the currently open institution (SPEC §3): sharing is
 * available to every member, member management + categories are admin/owner-only,
 * period export is available to everyone, leaving is always available. */
class InstitutionActionsBottomSheet(
    private val institutionName: String,
    private val isAdminOrOwner: Boolean,
    private val onShare: () -> Unit,
    private val onMembers: () -> Unit,
    private val onCategories: () -> Unit,
    private val onExport: () -> Unit,
    private val onLeave: () -> Unit,
) : BottomSheetDialogFragment() {

    private var _b: BottomSheetInstitutionActionsBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        val b = BottomSheetInstitutionActionsBinding.inflate(inflater, container, false)
        _b = b
        b.tvActionsInstitutionName.text = institutionName
        b.rowMembers.visibility = if (isAdminOrOwner) View.VISIBLE else View.GONE
        b.rowCategories.visibility = if (isAdminOrOwner) View.VISIBLE else View.GONE

        b.rowShare.setOnClickListener { onShare(); dismiss() }
        b.rowMembers.setOnClickListener { onMembers(); dismiss() }
        b.rowCategories.setOnClickListener { onCategories(); dismiss() }
        b.rowExport.setOnClickListener { onExport(); dismiss() }
        b.rowLeave.setOnClickListener { onLeave(); dismiss() }
        return b.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
