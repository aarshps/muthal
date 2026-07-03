package com.hora.muthal.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hora.muthal.databinding.ItemCategoryBinding
import com.hora.muthal.model.Category

class CategoryAdapter(private val onDelete: (Category) -> Unit) :
    RecyclerView.Adapter<CategoryAdapter.VH>() {

    private var items: List<Category> = emptyList()

    fun submit(list: List<Category>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(val b: ItemCategoryBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val c = items[position]
        h.b.tvCategoryName.text = c.name
        h.b.btnDeleteCategory.setOnClickListener { onDelete(c) }
    }
}
