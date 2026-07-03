package com.hora.muthal.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hora.muthal.databinding.ItemMemberBinding
import com.hora.muthal.model.Member
import com.hora.muthal.model.Role

class MemberAdapter(private val onClick: (Member) -> Unit) :
    RecyclerView.Adapter<MemberAdapter.VH>() {

    private var items: List<Member> = emptyList()

    fun submit(list: List<Member>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(val b: ItemMemberBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val m = items[position]
        h.b.tvMemberName.text = m.displayName.ifEmpty { m.email.ifEmpty { "Member" } }
        h.b.tvMemberEmail.text = m.email
        h.b.tvMemberEmail.visibility = if (m.email.isEmpty() || h.b.tvMemberName.text == m.email) android.view.View.GONE else android.view.View.VISIBLE
        h.b.tvMemberRole.text = when (m.role) {
            Role.OWNER -> "Owner"
            Role.ADMIN -> "Admin"
            else -> "Member"
        }
        h.b.root.setOnClickListener { onClick(m) }
    }
}
