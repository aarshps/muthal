package com.hora.muthal.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
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
import com.hora.muthal.ConfirmationBottomSheet
import com.hora.muthal.PreferenceHelper
import com.hora.muthal.R
import com.hora.muthal.SelectionBottomSheet
import com.hora.muthal.data.FirestoreRepo
import com.hora.muthal.databinding.ActivityMainBinding
import com.hora.muthal.databinding.SheetAddEntryBinding
import com.hora.muthal.databinding.SheetAddInstitutionBinding
import com.hora.muthal.model.Category
import com.hora.muthal.model.Entry
import com.hora.muthal.model.Institution
import com.hora.muthal.model.Membership
import com.hora.muthal.model.Role
import com.hora.muthal.util.BiometricAuthManager
import com.hora.muthal.util.CurrencyHelper
import com.hora.muthal.util.SummaryHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : BaseActivity() {

    private lateinit var b: ActivityMainBinding
    private val auth by lazy { FirebaseAuth.getInstance() }

    private var repo: FirestoreRepo? = null
    private var memberships: List<Membership> = emptyList()
    private var selectedMembership: Membership? = null
    private var selectedInstitution: Institution? = null
    private var categories: List<Category> = emptyList()
    private var entries: List<Entry> = emptyList()

    private var membershipsReg: ListenerRegistration? = null
    private var instReg: ListenerRegistration? = null
    private var categoriesReg: ListenerRegistration? = null
    private var entriesReg: ListenerRegistration? = null

    private val adapter = EntryAdapter { entry -> if (selectedMembership?.isAdminOrOwner == true) openEntrySheet(entry) }

    private val institutionTypes = listOf("Temple", "Church", "Library", "Other")
    private val dateFmt = SimpleDateFormat("d MMM yyyy", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private var pendingJoinCode: String? = null

    /** Splash keep-condition: hold until first frame is decided (auth + gate + data). */
    private var uiReady = false
    private var firstDataArrived = false
    private var biometricPassed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !uiReady }
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        pendingJoinCode = joinCodeFromIntent(intent)

        ViewCompat.setOnApplyWindowInsetsListener(b.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val density = resources.displayMetrics.density
            b.rvEntries.setPadding(
                b.rvEntries.paddingLeft, b.rvEntries.paddingTop,
                b.rvEntries.paddingRight, bars.bottom + (96 * density).toInt(),
            )
            val fabParams = b.fab.layoutParams as android.view.ViewGroup.MarginLayoutParams
            fabParams.bottomMargin = bars.bottom + (16 * density).toInt()
            b.fab.layoutParams = fabParams
            insets
        }
        b.rvEntries.layoutManager = LinearLayoutManager(this)
        b.rvEntries.adapter = adapter

        b.btnGoogle.setOnClickListener { signIn() }
        b.btnEmptyCreateInstitution.setOnClickListener { openInstitutionSheet() }
        b.btnEmptyJoinInstitution.setOnClickListener { openJoinSheet() }
        b.btnSwitchInstitution.setOnClickListener { openSwitcherSheet() }
        b.btnInstitutionActions.setOnClickListener { openInstitutionActionsSheet() }
        b.fab.setOnClickListener { if (selectedMembership?.isAdminOrOwner == true) openEntrySheet(null) }
        b.profileImage.setOnClickListener {
            PreferenceHelper.performClickHaptic(it)
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        b.swipeRefresh.setOnRefreshListener {
            PreferenceHelper.performHaptics(b.swipeRefresh, HapticFeedbackConstants.CLOCK_TICK)
            b.swipeRefresh.postDelayed({ b.swipeRefresh.isRefreshing = false }, 400)
        }

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        joinCodeFromIntent(intent)?.let { code ->
            if (repo != null) resolveAndConfirmJoin(code) else pendingJoinCode = code
        }
    }

    private fun joinCodeFromIntent(intent: Intent?): String? {
        val data = intent?.data ?: return intent?.getStringExtra(EXTRA_JOIN_CODE)
        val segments = data.pathSegments
        val idx = segments.indexOf("join")
        return if (idx >= 0 && idx + 1 < segments.size) segments[idx + 1].uppercase() else null
    }

    override fun onResume() {
        super.onResume()
        if (biometricPassed && auth.currentUser == null && repo != null) {
            detachInstitutionListeners()
            membershipsReg?.remove()
            repo = null
            memberships = emptyList(); selectedMembership = null; selectedInstitution = null
            categories = emptyList(); entries = emptyList()
            adapter.submit(emptyList(), "INR")
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
        loadAvatar()
        val uid = auth.currentUser?.uid ?: return
        if (repo != null) return
        repo = FirestoreRepo(uid)
        membershipsReg = repo!!.observeMemberships { list ->
            memberships = list
            onFirstData()
            onMembershipsChanged()
        }
        pendingJoinCode?.let { resolveAndConfirmJoin(it); pendingJoinCode = null }
    }

    private fun onFirstData() {
        if (!firstDataArrived) {
            firstDataArrived = true
            b.loadingSkeleton.root.visibility = View.GONE
            b.contentWrapper.visibility = View.VISIBLE
        }
        uiReady = true
    }

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

    // ── Institution switching ──

    private fun onMembershipsChanged() {
        val currentId = selectedMembership?.institutionId
        val next = if (currentId != null) memberships.firstOrNull { it.institutionId == currentId } ?: memberships.firstOrNull()
        else memberships.firstOrNull()

        if (next == null) {
            detachInstitutionListeners()
            selectedMembership = null; selectedInstitution = null; categories = emptyList(); entries = emptyList()
            b.institutionContentGroup.visibility = View.GONE
            b.noInstitutionsContainer.visibility = View.VISIBLE
            b.btnInstitutionActions.visibility = View.GONE
            b.fab.visibility = View.GONE
            return
        }

        b.noInstitutionsContainer.visibility = View.GONE
        b.institutionContentGroup.visibility = View.VISIBLE
        b.btnInstitutionActions.visibility = View.VISIBLE

        val switchedInstitution = currentId != next.institutionId
        selectedMembership = next
        updateHeader()
        if (switchedInstitution) attachInstitutionListeners(next.institutionId) else refreshEntries()
    }

    private fun selectInstitution(m: Membership) {
        if (m.institutionId == selectedMembership?.institutionId) return
        selectedMembership = m
        updateHeader()
        attachInstitutionListeners(m.institutionId)
    }

    private fun updateHeader() {
        val m = selectedMembership ?: return
        b.tvInstitutionName.text = m.institutionName
        b.tvMyRole.text = roleLabel(m.role)
        b.fab.visibility = if (m.isAdminOrOwner) View.VISIBLE else View.GONE
    }

    private fun roleLabel(role: String) = when (role) {
        Role.OWNER -> getString(R.string.owner_role)
        Role.ADMIN -> getString(R.string.admin_role)
        else -> getString(R.string.member_role)
    }

    private fun detachInstitutionListeners() {
        instReg?.remove(); categoriesReg?.remove(); entriesReg?.remove()
        instReg = null; categoriesReg = null; entriesReg = null
    }

    private fun attachInstitutionListeners(instId: String) {
        detachInstitutionListeners()
        val r = repo ?: return
        instReg = r.observeInstitution(instId) { inst -> selectedInstitution = inst }
        categoriesReg = r.observeCategories(instId) { cats -> categories = cats }
        entriesReg = r.observeEntries(instId) { list -> entries = list; refreshEntries() }
    }

    private fun refreshEntries() {
        val cur = selectedMembership?.currency ?: "INR"
        adapter.submit(entries, cur)
        b.emptyStateContainer.visibility = if (selectedMembership != null && entries.isEmpty()) View.VISIBLE else View.GONE
        val items = entries.map { SummaryHelper.Item(it.amount, it.type, it.date) }
        val s = SummaryHelper.summarize(items, System.currentTimeMillis())
        b.tvBalance.text = CurrencyHelper.format(s.balance, cur)
        b.tvMonth.text = "This month  +${CurrencyHelper.format(s.monthIncome, cur)}  /  −${CurrencyHelper.format(s.monthExpense, cur)}"
    }

    private fun openSwitcherSheet() {
        InstitutionSwitcherBottomSheet(
            memberships = memberships,
            onSelect = { selectInstitution(it) },
            onCreate = { openInstitutionSheet() },
            onJoin = { openJoinSheet() },
        ).show(supportFragmentManager, "switcher")
    }

    private fun openInstitutionActionsSheet() {
        val m = selectedMembership ?: return
        InstitutionActionsBottomSheet(
            institutionName = m.institutionName,
            isAdminOrOwner = m.isAdminOrOwner,
            isOwner = m.isOwner,
            onShare = { shareInstitution() },
            onMembers = { openManageMembers() },
            onCategories = { openCategories() },
            onExport = { openPeriodExport() },
            onLeave = { confirmLeaveInstitution() },
            onDelete = { confirmDeleteInstitution() },
        ).show(supportFragmentManager, "inst-actions")
    }

    private fun shareInstitution() {
        val inst = selectedInstitution
        if (inst == null) { toast("Still loading institution details, try again"); return }
        val r = repo ?: return
        val text = "Join \"${inst.name}\" on Muthal\n\nCode: ${inst.code}\n${r.joinLink(inst.code)}"
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        startActivity(Intent.createChooser(send, getString(R.string.share_institution)))
    }

    private fun openManageMembers() {
        val m = selectedMembership ?: return
        startActivity(
            Intent(this, ManageMembersActivity::class.java)
                .putExtra(ManageMembersActivity.EXTRA_INST_ID, m.institutionId)
                .putExtra(ManageMembersActivity.EXTRA_INST_NAME, m.institutionName)
                .putExtra(ManageMembersActivity.EXTRA_MY_ROLE, m.role)
        )
    }

    private fun openCategories() {
        val m = selectedMembership ?: return
        startActivity(
            Intent(this, CategoriesActivity::class.java)
                .putExtra(CategoriesActivity.EXTRA_INST_ID, m.institutionId)
                .putExtra(CategoriesActivity.EXTRA_INST_NAME, m.institutionName)
        )
    }

    private fun openPeriodExport() {
        val m = selectedMembership ?: return
        startActivity(
            Intent(this, PeriodExportActivity::class.java)
                .putExtra(PeriodExportActivity.EXTRA_INST_ID, m.institutionId)
                .putExtra(PeriodExportActivity.EXTRA_INST_NAME, m.institutionName)
                .putExtra(PeriodExportActivity.EXTRA_CURRENCY, m.currency)
        )
    }

    private fun showLoading(message: String) {
        b.loadingOverlay.visibility = View.VISIBLE
        b.textLoadingMessage.text = message
    }

    private fun hideLoading() {
        b.loadingOverlay.visibility = View.GONE
    }

    private fun confirmLeaveInstitution() {
        val m = selectedMembership ?: return
        val uid = auth.currentUser?.uid ?: return
        val ownerWarning = if (m.isOwner) " You are the owner — this institution will be left without one." else ""
        ConfirmationBottomSheet(
            title = getString(R.string.leave_institution),
            message = "Leave \"${m.institutionName}\"?$ownerWarning",
            positiveButtonText = getString(R.string.leave_institution),
            isDestructive = true,
            onConfirm = {
                showLoading("Leaving institution...")
                lifecycleScope.launch {
                    try { repo?.removeMember(m.institutionId, uid) }
                    catch (e: Exception) { toast("Couldn't leave: ${e.message}") }
                    finally { hideLoading() }
                }
            },
        ).show(supportFragmentManager, "leave")
    }

    /** Owner only (SPEC §3). Permanently deletes the institution and everything in it —
     * no undo, so this is the one confirmation in the app that spells out the stakes. */
    private fun confirmDeleteInstitution() {
        val m = selectedMembership ?: return
        ConfirmationBottomSheet(
            title = getString(R.string.delete_institution),
            message = "Permanently delete \"${m.institutionName}\"? This removes every entry, category, and member. This cannot be undone.",
            positiveButtonText = getString(R.string.delete_institution),
            isDestructive = true,
            onConfirm = {
                showLoading("Deleting institution...")
                lifecycleScope.launch {
                    try { repo?.deleteInstitution(m.institutionId) }
                    catch (e: Exception) { toast("Couldn't delete: ${e.message}") }
                    finally { hideLoading() }
                }
            },
        ).show(supportFragmentManager, "delete-institution")
    }

    // ── Join by code ──

    private fun openJoinSheet() {
        JoinInstitutionBottomSheet(onSubmit = { code -> resolveAndConfirmJoin(code) })
            .show(supportFragmentManager, "join")
    }

    private fun resolveAndConfirmJoin(code: String) {
        val r = repo ?: run { pendingJoinCode = code; return }
        showLoading("Resolving join code...")
        lifecycleScope.launch {
            try {
                val preview = r.resolveCode(code)
                hideLoading()
                if (preview == null) { toast("Invalid or expired code"); return@launch }
                ConfirmationBottomSheet(
                    title = getString(R.string.join_institution),
                    message = "Join \"${preview.institutionName}\"?",
                    positiveButtonText = getString(R.string.join),
                    onConfirm = {
                        showLoading("Joining ${preview.institutionName}...")
                        lifecycleScope.launch {
                            try {
                                val inst = r.joinInstitution(preview)
                                selectInstitution(Membership(inst.id, Role.MEMBER, inst.name, inst.type, inst.currency))
                                toast("Joined ${inst.name}")
                            } catch (e: Exception) { toast("Couldn't join: ${e.message}") }
                            finally { hideLoading() }
                        }
                    },
                ).show(supportFragmentManager, "join-confirm")
            } catch (e: Exception) {
                hideLoading()
                toast("Couldn't resolve code: ${e.message}")
            }
        }
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
                toast("Sign-in failed: ${e.message ?: e.type}")
            }
        }
    }

    // ── Institution create sheet ──
    private fun openInstitutionSheet() {
        val sb = SheetAddInstitutionBinding.inflate(layoutInflater)
        var type = institutionTypes.first()
        var currency = CurrencyHelper.CURRENCIES.first()
        sb.btnPickType.text = type
        sb.btnPickCurrency.text = "${currency.code}  ${currency.symbol}"

        sb.btnPickType.setOnClickListener {
            SelectionBottomSheet(getString(R.string.new_institution), institutionTypes.toTypedArray(), type) { chosen ->
                type = chosen; sb.btnPickType.text = chosen
            }.show(supportFragmentManager, "pick-type")
        }
        sb.btnPickCurrency.setOnClickListener {
            val options = CurrencyHelper.CURRENCIES.map { "${it.code}  ${it.symbol}" }.toTypedArray()
            SelectionBottomSheet("Currency", options, "${currency.code}  ${currency.symbol}") { chosen ->
                currency = CurrencyHelper.CURRENCIES.first { "${it.code}  ${it.symbol}" == chosen }
                sb.btnPickCurrency.text = chosen
            }.show(supportFragmentManager, "pick-currency")
        }

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(sb.root)
        sb.btnCreateInstitution.setOnClickListener {
            val name = sb.inputName.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) { sb.inputName.error = "Required"; return@setOnClickListener }
            val r = repo ?: return@setOnClickListener
            val finalType = type; val finalCurrency = currency.code
            dialog.dismiss()
            showLoading("Creating institution...")
            lifecycleScope.launch {
                try {
                    val inst = r.createInstitution(name, finalType, finalCurrency)
                    selectInstitution(Membership(inst.id, Role.OWNER, inst.name, inst.type, inst.currency))
                } catch (e: Exception) { toast("Couldn't create institution: ${e.message}") }
                finally { hideLoading() }
            }
        }
        dialog.show()
    }

    // ── Entry sheet ──
    private fun openEntrySheet(entry: Entry?) {
        val m = selectedMembership ?: return
        val sb = SheetAddEntryBinding.inflate(layoutInflater)
        var currentType = entry?.type ?: "income"
        var currentDateMillis = entry?.date ?: System.currentTimeMillis()
        var currentCategory = entry?.category.orEmpty()

        fun categoryOptions() = categories.filter { it.kind == currentType }.map { it.name }

        fun applyCategoryButton() {
            val opts = categoryOptions()
            if (currentCategory.isEmpty() || currentCategory !in opts) currentCategory = opts.firstOrNull().orEmpty()
            sb.btnPickCategory.text = currentCategory.ifEmpty { getString(R.string.category) }
        }

        sb.toggleType.check(if (currentType == "income") sb.btnIncome.id else sb.btnExpense.id)
        applyCategoryButton()
        sb.btnPickDate.text = dateFmt.format(Date(currentDateMillis))

        sb.toggleType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            currentType = if (checkedId == sb.btnIncome.id) "income" else "expense"
            currentCategory = ""
            applyCategoryButton()
        }

        sb.btnPickCategory.setOnClickListener {
            val opts = categoryOptions()
            if (opts.isEmpty()) { toast("No ${currentType} categories yet — add one from Institution > Categories"); return@setOnClickListener }
            SelectionBottomSheet(getString(R.string.category), opts.toTypedArray(), currentCategory) { chosen ->
                currentCategory = chosen; sb.btnPickCategory.text = chosen
            }.show(supportFragmentManager, "pick-category")
        }

        sb.btnPickDate.setOnClickListener {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = currentDateMillis }
            DatePickerDialog(
                this,
                { _, y, mo, d ->
                    val picked = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                    picked.clear(); picked.set(y, mo, d)
                    currentDateMillis = picked.timeInMillis
                    sb.btnPickDate.text = dateFmt.format(Date(currentDateMillis))
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
            ).show()
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
            if (currentCategory.isEmpty()) { toast("Pick a category"); return@setOnClickListener }
            val note = sb.inputNote.text?.toString()?.trim().orEmpty()
            repo?.saveEntry(m.institutionId, entry?.id, currentDateMillis, amount, currentType, currentCategory, note,
                onFailure = { e -> toast("Failed to save entry: ${e.message}") }
            )
            dialog.dismiss()
        }
        sb.btnDelete.setOnClickListener {
            entry?.let {
                repo?.deleteEntry(m.institutionId, it.id,
                    onFailure = { e -> toast("Failed to delete entry: ${e.message}") }
                )
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        super.onDestroy()
        membershipsReg?.remove()
        detachInstitutionListeners()
    }

    companion object {
        const val EXTRA_JOIN_CODE = "join_code"
    }
}
