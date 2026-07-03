package com.hora.muthal.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.hora.muthal.BaseActivity
import com.hora.muthal.R
import com.hora.muthal.data.FirestoreRepo
import com.hora.muthal.databinding.ActivityPeriodExportBinding
import com.hora.muthal.model.Entry
import com.hora.muthal.util.CurrencyHelper
import com.hora.muthal.util.SummaryHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Start/end date range → opening balance, in-range entries, closing balance (SPEC §7). */
class PeriodExportActivity : BaseActivity() {

    private lateinit var b: ActivityPeriodExportBinding
    private lateinit var repo: FirestoreRepo
    private lateinit var instId: String
    private lateinit var instName: String
    private var currency: String = "INR"

    private var startMillis: Long = 0
    private var endInclusiveMillis: Long = 0
    private var lastResult: SummaryHelper.PeriodSummary? = null
    private var lastInRange: List<Entry> = emptyList()

    private val dateFmt = SimpleDateFormat("d MMM yyyy", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    private val adapter = EntryAdapter { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPeriodExportBinding.inflate(layoutInflater)
        setContentView(b.root)

        instId = intent.getStringExtra(EXTRA_INST_ID) ?: run { finish(); return }
        instName = intent.getStringExtra(EXTRA_INST_NAME).orEmpty()
        currency = intent.getStringExtra(EXTRA_CURRENCY) ?: "INR"

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back)
        supportActionBar?.title = instName

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run { finish(); return }
        repo = FirestoreRepo(uid)

        b.rvExportEntries.layoutManager = LinearLayoutManager(this)
        b.rvExportEntries.adapter = adapter

        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(Calendar.DAY_OF_MONTH, 1)
        startMillis = startOfDay(cal.timeInMillis)
        endInclusiveMillis = endOfDay(System.currentTimeMillis())
        b.btnStartDate.text = dateFmt.format(Date(startMillis))
        b.btnEndDate.text = dateFmt.format(Date(endInclusiveMillis))

        b.btnStartDate.setOnClickListener { pickDate(startMillis) { startMillis = startOfDay(it); b.btnStartDate.text = dateFmt.format(Date(startMillis)) } }
        b.btnEndDate.setOnClickListener { pickDate(endInclusiveMillis) { endInclusiveMillis = endOfDay(it); b.btnEndDate.text = dateFmt.format(Date(endInclusiveMillis)) } }
        b.btnRunExport.setOnClickListener { runExport() }
        b.btnShareExport.setOnClickListener { shareExport() }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun startOfDay(millis: Long): Long {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun endOfDay(millis: Long): Long {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
        c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59); c.set(Calendar.SECOND, 59); c.set(Calendar.MILLISECOND, 999)
        return c.timeInMillis
    }

    private fun pickDate(current: Long, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = current }
        DatePickerDialog(
            this,
            { _, y, mo, d ->
                val picked = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                picked.clear(); picked.set(y, mo, d)
                onPicked(picked.timeInMillis)
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun runExport() {
        if (endInclusiveMillis < startMillis) { Toast.makeText(this, "End date is before start date", Toast.LENGTH_LONG).show(); return }
        lifecycleScope.launch {
            try {
                val all = repo.getEntriesOnce(instId)
                val items = all.map { SummaryHelper.Item(it.amount, it.type, it.date) }
                val result = SummaryHelper.periodSummarize(items, startMillis, endInclusiveMillis)
                val inRange = all.filter { it.date in startMillis..endInclusiveMillis }
                lastResult = result
                lastInRange = inRange
                showResult(result, inRange)
            } catch (e: Exception) {
                Toast.makeText(this@PeriodExportActivity, "Couldn't run export: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showResult(result: SummaryHelper.PeriodSummary, inRange: List<Entry>) {
        b.resultCard.visibility = View.VISIBLE
        b.btnShareExport.visibility = View.VISIBLE
        b.tvOpeningBalance.text = CurrencyHelper.format(result.openingBalance, currency)
        b.tvClosingBalance.text = CurrencyHelper.format(result.closingBalance, currency)
        b.tvPeriodTotals.text = "+${CurrencyHelper.format(result.periodIncome, currency)}  /  " +
            "−${CurrencyHelper.format(result.periodExpense, currency)}  ·  ${result.entryCount} ${if (result.entryCount == 1) "entry" else "entries"}"
        adapter.submit(inRange.sortedByDescending { it.date }, currency)
    }

    private fun shareExport() {
        val result = lastResult ?: return
        val sb = StringBuilder()
        sb.append(instName).append(" — period export\n")
        sb.append(dateFmt.format(Date(startMillis))).append(" to ").append(dateFmt.format(Date(endInclusiveMillis))).append("\n\n")
        sb.append("Opening balance: ").append(CurrencyHelper.format(result.openingBalance, currency)).append("\n")
        for (e in lastInRange.sortedBy { it.date }) {
            val sign = if (e.type == "income") "+" else "-"
            sb.append(dateFmt.format(Date(e.date))).append("  ")
                .append(e.category.ifEmpty { "Uncategorized" }).append("  ")
                .append(sign).append(CurrencyHelper.format(e.amount, currency))
            if (e.note.isNotEmpty()) sb.append("  (").append(e.note).append(")")
            sb.append("\n")
        }
        sb.append("\nIncome: +").append(CurrencyHelper.format(result.periodIncome, currency)).append("\n")
        sb.append("Expense: -").append(CurrencyHelper.format(result.periodExpense, currency)).append("\n")
        sb.append("Closing balance: ").append(CurrencyHelper.format(result.closingBalance, currency)).append("\n")
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, sb.toString())
        }, getString(R.string.share)))
    }

    companion object {
        const val EXTRA_INST_ID = "inst_id"
        const val EXTRA_INST_NAME = "inst_name"
        const val EXTRA_CURRENCY = "currency"
    }
}
