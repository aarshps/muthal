package com.hora.muthal.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.hora.muthal.BaseActivity
import com.hora.muthal.ConfirmationBottomSheet
import com.hora.muthal.R
import com.hora.muthal.SelectionBottomSheet
import com.hora.muthal.data.FirestoreRepo
import com.hora.muthal.databinding.ActivityManageMembersBinding
import com.hora.muthal.model.Member
import com.hora.muthal.model.Role
import kotlinx.coroutines.launch

/** Owner/admin-only member list (SPEC §3); only the owner can promote / demote / remove. */
class ManageMembersActivity : BaseActivity() {

    private lateinit var b: ActivityManageMembersBinding
    private lateinit var repo: FirestoreRepo
    private lateinit var instId: String
    private var myRole: String = Role.MEMBER
    private var instReg: ListenerRegistration? = null
    private var membersReg: ListenerRegistration? = null
    private val adapter = MemberAdapter { onMemberClick(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityManageMembersBinding.inflate(layoutInflater)
        setContentView(b.root)

        instId = intent.getStringExtra(EXTRA_INST_ID) ?: run { finish(); return }
        myRole = intent.getStringExtra(EXTRA_MY_ROLE) ?: Role.MEMBER
        val instName = intent.getStringExtra(EXTRA_INST_NAME).orEmpty()

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back)
        supportActionBar?.title = instName

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run { finish(); return }
        repo = FirestoreRepo(uid)

        b.rvMembers.layoutManager = LinearLayoutManager(this)
        b.rvMembers.adapter = adapter

        b.btnShareCode.setOnClickListener { shareCode() }

        instReg = repo.observeInstitution(instId) { inst -> b.tvJoinCode.text = inst?.code.orEmpty() }
        membersReg = repo.observeMembers(instId) { adapter.submit(it) }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun shareCode() {
        val code = b.tvJoinCode.text?.toString().orEmpty()
        if (code.isEmpty()) return
        val text = "Join us on Muthal\n\nCode: $code\n${repo.joinLink(code)}"
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
        }, getString(R.string.share_institution)))
    }

    private fun onMemberClick(member: Member) {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid
        if (myRole != Role.OWNER || member.uid == myUid || member.role == Role.OWNER) return

        val options = buildList {
            if (member.role == Role.MEMBER) add(getString(R.string.promote_to_admin))
            if (member.role == Role.ADMIN) add(getString(R.string.demote_to_member))
            add(getString(R.string.remove_member))
        }
        SelectionBottomSheet(member.displayName.ifEmpty { member.email }, options.toTypedArray(), null) { chosen ->
            when (chosen) {
                getString(R.string.promote_to_admin) -> setRole(member, Role.ADMIN)
                getString(R.string.demote_to_member) -> setRole(member, Role.MEMBER)
                getString(R.string.remove_member) -> confirmRemove(member)
            }
        }.show(supportFragmentManager, "member-actions")
    }

    private fun setRole(member: Member, role: String) {
        lifecycleScope.launch {
            try { repo.setMemberRole(instId, member.uid, role) }
            catch (e: Exception) { Toast.makeText(this@ManageMembersActivity, "Couldn't update role: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun confirmRemove(member: Member) {
        ConfirmationBottomSheet(
            title = getString(R.string.remove_member),
            message = "Remove ${member.displayName.ifEmpty { member.email }} from this institution?",
            positiveButtonText = getString(R.string.remove_member),
            isDestructive = true,
            onConfirm = {
                lifecycleScope.launch {
                    try { repo.removeMember(instId, member.uid) }
                    catch (e: Exception) { Toast.makeText(this@ManageMembersActivity, "Couldn't remove: ${e.message}", Toast.LENGTH_LONG).show() }
                }
            },
        ).show(supportFragmentManager, "remove-member")
    }

    override fun onDestroy() {
        super.onDestroy()
        instReg?.remove()
        membersReg?.remove()
    }

    companion object {
        const val EXTRA_INST_ID = "inst_id"
        const val EXTRA_INST_NAME = "inst_name"
        const val EXTRA_MY_ROLE = "my_role"
    }
}
