package com.hora.muthal.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hora.muthal.databinding.ItemMembershipBinding
import com.hora.muthal.model.Membership
import com.hora.muthal.model.Role

class MembershipAdapter(private val onClick: (Membership) -> Unit) :
    RecyclerView.Adapter<MembershipAdapter.VH>() {

    private var items: List<Membership> = emptyList()

    fun submit(list: List<Membership>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(val b: ItemMembershipBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemMembershipBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val m = items[position]
        h.b.tvName.text = m.institutionName
        h.b.tvType.text = "${m.institutionType}  ·  ${m.currency}"
        h.b.tvRole.text = when (m.role) {
            Role.OWNER -> "Owner"
            Role.ADMIN -> "Admin"
            else -> "Member"
        }
        h.b.root.setOnClickListener { onClick(m) }
    }
}
