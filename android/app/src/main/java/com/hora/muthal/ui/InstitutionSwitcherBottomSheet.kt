package com.hora.muthal.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hora.muthal.databinding.BottomSheetInstitutionSwitcherBinding
import com.hora.muthal.model.Membership

/** Replaces the old glitchy Spinner (see MainActivity history) with a proper
 * bottom-sheet switcher, consistent with the family's bottom-sheet picker language. */
class InstitutionSwitcherBottomSheet(
    private val memberships: List<Membership>,
    private val onSelect: (Membership) -> Unit,
    private val onCreate: () -> Unit,
    private val onJoin: () -> Unit,
) : BottomSheetDialogFragment() {

    private var _b: BottomSheetInstitutionSwitcherBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        val b = BottomSheetInstitutionSwitcherBinding.inflate(inflater, container, false)
        _b = b
        val adapter = MembershipAdapter { m -> onSelect(m); dismiss() }
        b.rvMemberships.layoutManager = LinearLayoutManager(requireContext())
        b.rvMemberships.adapter = adapter
        adapter.submit(memberships)
        b.btnSwitcherCreate.setOnClickListener { onCreate(); dismiss() }
        b.btnSwitcherJoin.setOnClickListener { onJoin(); dismiss() }
        return b.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
