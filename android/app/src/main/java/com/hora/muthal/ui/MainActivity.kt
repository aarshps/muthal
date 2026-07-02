package com.hora.muthal.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.ListenerRegistration
import com.hora.muthal.BaseActivity
import com.hora.muthal.PreferenceHelper
import com.hora.muthal.R
import com.hora.muthal.data.FirestoreRepo
import com.hora.muthal.util.BiometricAuthManager
import com.hora.muthal.databinding.ActivityMainBinding
import com.hora.muthal.databinding.SheetAddEntryBinding
import com.hora.muthal.databinding.SheetAddInstitutionBinding
import com.hora.muthal.model.Entry
import com.hora.muthal.model.Institution
import com.hora.muthal.util.Categories
import com.hora.muthal.util.CurrencyHelper
import com.hora.muthal.util.SummaryHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class MainActivity : BaseActivity() {

    private lateinit var b: ActivityMainBinding
    private val auth by lazy { FirebaseAuth.getInstance() }

    private var repo: FirestoreRepo? = null
    private var institutions: List<Institution> = emptyList()
    private var selectedInst: Institution? = null
    private var allEntries: List<Entry> = emptyList()
    private var instReg: ListenerRegistration? = null
    private var entryReg: ListenerRegistration? = null

    private val adapter = EntryAdapter { openEntrySheet(it) }

    private val institutionTypes = listOf("Temple", "Church", "Library", "Other")

    /** Splash keep-condition: hold until first frame is decided (auth + gate + data). */
    private var uiReady = false
    private var firstDataArrived = false
    private var biometricPassed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Family splash standard (splash-and-home-standards): install BEFORE the
        // BaseActivity theme switch, hold until the first real frame is ready.
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !uiReady }
        // BaseActivity applies edge-to-edge, the brand-font theme, and re-applies
        // Dynamic Colors per-activity (family standard).
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        b.rvEntries.layoutManager = LinearLayoutManager(this)
        b.rvEntries.adapter = adapter

        b.btnGoogle.setOnClickListener { signIn() }
        b.btnAddInstitution.setOnClickListener { openInstitutionSheet() }
        b.fab.setOnClickListener { if (selectedInst != null) openEntrySheet(null) else openInstitutionSheet() }
        b.profileImage.setOnClickListener {
            PreferenceHelper.performClickHaptic(it)
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        b.swipeRefresh.setOnRefreshListener {
            // Firestore listeners are live; the gesture is just an acknowledgement.
            PreferenceHelper.performHaptics(b.swipeRefresh, HapticFeedbackConstants.CLOCK_TICK)
            b.swipeRefresh.postDelayed({ b.swipeRefresh.isRefreshing = false }, 400)
        }

        // App-Lock gate (family standard): biometric before any content when enabled.
        if (auth.currentUser != null && PreferenceHelper.isBiometricEnabled(this)) {
            BiometricAuthManager.authenticate(
                this,
                onSuccess = { biometricPassed = true; render() },
                onError = { finish() },
            )
        } else {
            biometricPassed = true
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        // Coming back from Settings after a sign-out / account deletion.
        if (biometricPassed && auth.currentUser == null && repo != null) {
            instReg?.remove(); entryReg?.remove()
            repo = null
            institutions = emptyList(); selectedInst = null; allEntries = emptyList()
            adapter.submit(emptyList())
            render()
        }
    }

    private fun render() {
        if (!biometricPassed) return
        if (auth.currentUser != null) onSignedIn() else onSignedOut()
    }

    private fun onSignedOut() {
        uiReady = true
        b.signInGroup.visibility = View.VISIBLE
        b.appBar.visibility = View.GONE
        b.swipeRefresh.visibility = View.GONE
        b.fab.visibility = View.GONE
    }

    private fun onSignedIn() {
        b.signInGroup.visibility = View.GONE
        b.appBar.visibility = View.VISIBLE
        b.swipeRefresh.visibility = View.VISIBLE
        b.fab.visibility = View.VISIBLE
        loadAvatar()
        val uid = auth.currentUser?.uid ?: return
        if (repo != null) return   // listeners already attached
        repo = FirestoreRepo(uid)
        instReg = repo!!.observeInstitutions { list ->
            institutions = list
            onFirstData()
            setupInstitutionSpinner()
        }
        entryReg = repo!!.observeEntries { list ->
            allEntries = list
            onFirstData()
            refreshEntries()
        }
    }

    /** First snapshot: swap the skeleton for real content, release the splash. */
    private fun onFirstData() {
        if (!firstDataArrived) {
            firstDataArrived = true
            b.loadingSkeleton.root.visibility = View.GONE
            b.contentWrapper.visibility = View.VISIBLE
        }
        uiReady = true
    }

    /** Google account photo → toolbar avatar (lightweight loader, family standard). */
    private fun loadAvatar() {
        auth.currentUser?.photoUrl?.let { url ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val bmp = BitmapFactory.decodeStream(URL(url.toString()).openStream())
                    withContext(Dispatchers.Main) { b.profileImage.setImageBitmap(bmp) }
                } catch (_: Exception) { }
            }
        }
    }

    private fun setupInstitutionSpinner() {
        val ad = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            institutions.map { it.name },
        )
        b.spinnerInstitution.adapter = ad
        if (selectedInst == null || institutions.none { it.id == selectedInst?.id }) {
            selectedInst = institutions.firstOrNull()
        }
        selectedInst?.let { s ->
            b.spinnerInstitution.setSelection(
                institutions.indexOfFirst { it.id == s.id }.coerceAtLeast(0)
            )
        }
        b.spinnerInstitution.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                selectedInst = institutions.getOrNull(pos)
                refreshEntries()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        refreshEntries()
    }

    private fun refreshEntries() {
        val inst = selectedInst
        val filtered = if (inst != null) allEntries.filter { it.institutionId == inst.id } else emptyList()
        adapter.submit(filtered)
        b.emptyStateContainer.visibility = if (inst != null && filtered.isEmpty()) View.VISIBLE else View.GONE
        val cur = inst?.currency ?: "INR"
        val items = filtered.map { SummaryHelper.Item(it.amount, it.type, it.date) }
        val s = SummaryHelper.summarize(items, System.currentTimeMillis())
        b.tvBalance.text = CurrencyHelper.format(s.balance, cur)
        b.tvMonth.text = "This month  +${CurrencyHelper.format(s.monthIncome, cur)}  /  −${CurrencyHelper.format(s.monthExpense, cur)}"
    }

    // ── Auth ──
    private fun signIn() {
        val credManager = CredentialManager.create(this)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(getString(R.string.default_web_client_id))
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
        lifecycleScope.launch {
            try {
                val result = credManager.getCredential(this@MainActivity, request)
                val cred = result.credential
                if (cred is CustomCredential &&
                    cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val gid = GoogleIdTokenCredential.createFrom(cred.data)
                    val firebaseCred = GoogleAuthProvider.getCredential(gid.idToken, null)
                    auth.signInWithCredential(firebaseCred).addOnCompleteListener { t ->
                        if (t.isSuccessful) render()
                        else toast("Sign-in failed: ${t.exception?.message}")
                    }
                }
            } catch (e: GetCredentialCancellationException) {
                toast("Sign-in cancelled")
            } catch (e: NoCredentialException) {
                toast("No Google account available on this device")
            } catch (e: GetCredentialException) {
                // Don't mask real failures (e.g. OAuth client / SHA mismatch) as a cancel.
                toast("Sign-in failed: ${e.message ?: e.type}")
            }
        }
    }

    // ── Institution sheet ──
    private fun openInstitutionSheet() {
        val sb = SheetAddInstitutionBinding.inflate(layoutInflater)
        sb.spinnerType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, institutionTypes)
        sb.spinnerCurrency.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            CurrencyHelper.CURRENCIES.map { "${it.code}  ${it.symbol}" },
        )
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(sb.root)
        sb.btnCreateInstitution.setOnClickListener {
            val name = sb.inputName.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) { sb.inputName.error = "Required"; return@setOnClickListener }
            val type = institutionTypes[sb.spinnerType.selectedItemPosition]
            val currency = CurrencyHelper.CURRENCIES[sb.spinnerCurrency.selectedItemPosition].code
            repo?.addInstitution(name, type, currency) { id ->
                selectedInst = Institution(id = id, name = name, type = type, currency = currency)
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    // ── Entry sheet ──
    private fun openEntrySheet(entry: Entry?) {
        val inst = selectedInst ?: return
        val sb = SheetAddEntryBinding.inflate(layoutInflater)
        var currentType = entry?.type ?: "income"

        fun applyCategorySpinner() {
            sb.spinnerCategory.adapter = ArrayAdapter(
                this, android.R.layout.simple_spinner_dropdown_item, Categories.forType(currentType),
            )
            entry?.category?.let { cat ->
                val idx = Categories.forType(currentType).indexOf(cat)
                if (idx >= 0) sb.spinnerCategory.setSelection(idx)
            }
        }

        sb.toggleType.check(if (currentType == "income") sb.btnIncome.id else sb.btnExpense.id)
        applyCategorySpinner()
        sb.toggleType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            currentType = if (checkedId == sb.btnIncome.id) "income" else "expense"
            applyCategorySpinner()
        }

        entry?.let {
            sb.sheetTitle.text = "Edit entry"
            sb.inputAmount.setText(if (it.amount % 1.0 == 0.0) it.amount.toLong().toString() else it.amount.toString())
            sb.inputNote.setText(it.note)
            sb.btnDelete.visibility = View.VISIBLE
        }

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(sb.root)

        sb.btnSaveEntry.setOnClickListener {
            val amount = sb.inputAmount.text?.toString()?.toDoubleOrNull() ?: 0.0
            if (amount <= 0.0) { sb.inputAmount.error = "Enter an amount"; return@setOnClickListener }
            val category = (sb.spinnerCategory.selectedItem as? String).orEmpty()
            val note = sb.inputNote.text?.toString()?.trim().orEmpty()
            val dateMillis = entry?.date ?: System.currentTimeMillis()
            repo?.saveEntry(inst, entry?.id, dateMillis, amount, currentType, category, note)
            dialog.dismiss()
        }
        sb.btnDelete.setOnClickListener {
            entry?.let { repo?.deleteEntry(it.institutionId, it.id) }
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        super.onDestroy()
        instReg?.remove(); entryReg?.remove()
    }
}
