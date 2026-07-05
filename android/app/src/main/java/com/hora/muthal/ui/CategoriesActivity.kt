package com.hora.muthal.ui

import android.os.Bundle
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.hora.muthal.BaseActivity
import com.hora.muthal.ConfirmationBottomSheet
import com.hora.muthal.R
import com.hora.muthal.data.FirestoreRepo
import com.hora.muthal.databinding.ActivityCategoriesBinding
import com.hora.muthal.databinding.SheetAddCategoryBinding
import androidx.recyclerview.widget.LinearLayoutManager
import com.hora.muthal.model.Category

/** Admin/owner-only category CRUD, scoped to one institution (SPEC §5). */
class CategoriesActivity : BaseActivity() {

    private lateinit var b: ActivityCategoriesBinding
    private lateinit var repo: FirestoreRepo
    private lateinit var instId: String
    private var categoriesReg: ListenerRegistration? = null

    private val incomeAdapter = CategoryAdapter { confirmDelete(it) }
    private val expenseAdapter = CategoryAdapter { confirmDelete(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityCategoriesBinding.inflate(layoutInflater)
        setContentView(b.root)

        instId = intent.getStringExtra(EXTRA_INST_ID) ?: run { finish(); return }
        val instName = intent.getStringExtra(EXTRA_INST_NAME).orEmpty()

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back)
        supportActionBar?.title = instName

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run { finish(); return }
        repo = FirestoreRepo(uid)

        b.rvIncomeCategories.layoutManager = LinearLayoutManager(this)
        b.rvIncomeCategories.adapter = incomeAdapter
        b.rvExpenseCategories.layoutManager = LinearLayoutManager(this)
        b.rvExpenseCategories.adapter = expenseAdapter

        b.fabAddCategory.setOnClickListener { openAddCategorySheet() }

        categoriesReg = repo.observeCategories(instId) { cats ->
            incomeAdapter.submit(cats.filter { it.kind == "income" })
            expenseAdapter.submit(cats.filter { it.kind == "expense" })
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun openAddCategorySheet() {
        val sb = SheetAddCategoryBinding.inflate(layoutInflater)
        sb.toggleKind.check(sb.btnKindIncome.id)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(sb.root)
        sb.btnSaveCategory.setOnClickListener {
            val name = sb.inputCategoryName.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) { sb.inputCategoryName.error = "Required"; return@setOnClickListener }
            val kind = if (sb.toggleKind.checkedButtonId == sb.btnKindIncome.id) "income" else "expense"
            repo.addCategory(instId, name, kind, onFailure = { e ->
                Toast.makeText(this, "Failed to add category: ${e.message}", Toast.LENGTH_LONG).show()
            })
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun confirmDelete(category: Category) {
        ConfirmationBottomSheet(
            title = getString(R.string.delete),
            message = "Delete category \"${category.name}\"? Existing entries keep it as text but it will no longer be selectable.",
            positiveButtonText = getString(R.string.delete),
            isDestructive = true,
            onConfirm = {
                repo.deleteCategory(instId, category.id, onFailure = { e ->
                    Toast.makeText(this, "Failed to delete category: ${e.message}", Toast.LENGTH_LONG).show()
                })
            },
        ).show(supportFragmentManager, "delete-category")
    }

    override fun onDestroy() {
        super.onDestroy()
        categoriesReg?.remove()
    }

    companion object {
        const val EXTRA_INST_ID = "inst_id"
        const val EXTRA_INST_NAME = "inst_name"
    }
}
