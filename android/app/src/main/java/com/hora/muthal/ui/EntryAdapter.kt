package com.hora.muthal.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.hora.muthal.R
import com.hora.muthal.databinding.ItemEntryBinding
import com.hora.muthal.model.Entry
import com.hora.muthal.util.CurrencyHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class EntryAdapter(private val onClick: (Entry) -> Unit) :
    RecyclerView.Adapter<EntryAdapter.VH>() {

    private var items: List<Entry> = emptyList()
    private var currency: String = "INR"

    private val dateFmt = SimpleDateFormat("d MMM yyyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun submit(list: List<Entry>, currencyCode: String) {
        items = list
        currency = currencyCode
        notifyDataSetChanged()
    }

    class VH(val b: ItemEntryBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val e = items[position]
        h.b.tvCategory.text = e.category.ifEmpty { "Uncategorized" }
        h.b.tvSub.text = if (e.note.isNotEmpty()) e.note else dateFmt.format(Date(e.date))
        val sign = if (e.type == "income") "+" else "−"
        h.b.tvAmount.text = sign + CurrencyHelper.format(e.amount, currency)
        val colorRes = if (e.type == "income") R.color.income else R.color.expense
        h.b.tvAmount.setTextColor(ContextCompat.getColor(h.b.tvAmount.context, colorRes))
        h.b.root.setOnClickListener { onClick(e) }
    }
}
