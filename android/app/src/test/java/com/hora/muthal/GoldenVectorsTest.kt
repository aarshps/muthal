package com.hora.muthal

import com.hora.muthal.util.Categories
import com.hora.muthal.util.CurrencyHelper
import com.hora.muthal.util.SummaryHelper
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.Instant

/**
 * Parity guard: runs the shared, language-neutral vectors in
 * shared/domain/golden-vectors.json through the Android implementations, so
 * Android provably agrees with web + iOS. See shared/domain/SPEC.md.
 */
class GoldenVectorsTest {

    private val vectors: JSONObject by lazy {
        val candidates = listOf(
            "../../shared/domain/golden-vectors.json",
            "../shared/domain/golden-vectors.json",
            "shared/domain/golden-vectors.json",
        )
        val f = candidates.map { File(it) }.firstOrNull { it.exists() }
            ?: error("golden-vectors.json not found from ${File(".").absolutePath}")
        JSONObject(f.readText())
    }

    @Test
    fun currencyFormat() {
        val arr = vectors.getJSONObject("currency").getJSONArray("format")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            assertEquals(
                o.getString("expected"),
                CurrencyHelper.format(o.getDouble("amount"), o.getString("code")),
            )
        }
    }

    @Test
    fun currencyCompact() {
        val arr = vectors.getJSONObject("currency").getJSONArray("compact")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            assertEquals(o.getString("expected"), CurrencyHelper.compact(o.getDouble("amount")))
        }
    }

    @Test
    fun categories() {
        val arr = vectors.getJSONArray("categories")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val expJson = o.getJSONArray("expected")
            val exp = (0 until expJson.length()).map { expJson.getString(it) }
            assertEquals(exp, Categories.forType(o.getString("type")))
        }
    }

    @Test
    fun summary() {
        val arr = vectors.getJSONArray("summary")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val now = Instant.parse(o.getString("now")).toEpochMilli()
            val entriesJson = o.getJSONArray("entries")
            val items = (0 until entriesJson.length()).map {
                val e = entriesJson.getJSONObject(it)
                SummaryHelper.Item(
                    e.getDouble("amount"),
                    e.getString("type"),
                    Instant.parse(e.getString("date")).toEpochMilli(),
                )
            }
            val s = SummaryHelper.summarize(items, now)
            val exp = o.getJSONObject("expected")
            assertEquals(exp.getDouble("totalIncome"), s.totalIncome, 0.001)
            assertEquals(exp.getDouble("totalExpense"), s.totalExpense, 0.001)
            assertEquals(exp.getDouble("balance"), s.balance, 0.001)
            assertEquals(exp.getDouble("monthIncome"), s.monthIncome, 0.001)
            assertEquals(exp.getDouble("monthExpense"), s.monthExpense, 0.001)
            assertEquals(exp.getDouble("monthBalance"), s.monthBalance, 0.001)
        }
    }
}
