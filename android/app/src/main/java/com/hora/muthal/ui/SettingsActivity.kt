package com.hora.muthal.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.hora.muthal.BaseActivity
import com.hora.muthal.AboutBottomSheet
import com.hora.muthal.ConfirmationBottomSheet
import com.hora.muthal.PreferenceHelper
import com.hora.muthal.R
import com.hora.muthal.databinding.ActivitySettingsBinding
import com.hora.muthal.util.BiometricAuthManager
import com.hora.muthal.util.ChipHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Family-standard Settings screen (hora-core `settings-page-standards`): collapsing
 * "Settings" title, profile header card, titled grouped cards (Appearance / Preferences /
 * About), bottom full-width Sign out + Delete account.
 */
class SettingsActivity : BaseActivity() {

    private lateinit var b: ActivitySettingsBinding
    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)

        setSupportActionBar(b.settingsToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        PreferenceHelper.attachNestedScrollHaptics(b.settingsScroll)

        setupProfile()
        setupAppearance()
        setupPreferences()
        setupAbout()
        setupAccountButtons()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun setupProfile() {
        val user = auth.currentUser
        b.tvProfileName.text = user?.displayName ?: getString(R.string.app_name)
        b.tvProfileEmail.text = user?.email ?: ""
        user?.photoUrl?.let { url ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val bmp = BitmapFactory.decodeStream(URL(url.toString()).openStream())
                    withContext(Dispatchers.Main) { b.settingsAvatar.setImageBitmap(bmp) }
                } catch (_: Exception) { }
            }
        }
    }

    private fun setupAppearance() {
        ChipHelper.styleChipGroup(b.themeChips)
        when (PreferenceHelper.getThemeMode(this)) {
            AppCompatDelegate.MODE_NIGHT_NO -> b.chipThemeLight.isChecked = true
            AppCompatDelegate.MODE_NIGHT_YES -> b.chipThemeDark.isChecked = true
            else -> b.chipThemeSystem.isChecked = true
        }
        b.themeChips.setOnCheckedStateChangeListener { group, checkedIds ->
            PreferenceHelper.performHaptics(group, HapticFeedbackConstants.CLOCK_TICK)
            ChipHelper.styleChipGroup(group)
            val mode = when (checkedIds.firstOrNull()) {
                R.id.chipThemeLight -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.chipThemeDark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            PreferenceHelper.setThemeMode(this, mode)
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    private fun setupPreferences() {
        b.switchHaptics.isChecked = PreferenceHelper.isHapticsEnabled(this)
        b.switchHaptics.setOnCheckedChangeListener { view, checked ->
            PreferenceHelper.setHapticsEnabled(this, checked)
            if (checked) PreferenceHelper.performClickHaptic(view)
        }

        b.switchAppLock.isChecked = PreferenceHelper.isBiometricEnabled(this)
        b.switchAppLock.setOnCheckedChangeListener { view, checked ->
            if (checked && !BiometricAuthManager.isBiometricAvailable(this)) {
                view.isChecked = false
                Toast.makeText(this, "No biometrics enrolled on this device", Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }
            PreferenceHelper.setBiometricEnabled(this, checked)
            PreferenceHelper.performClickHaptic(view)
        }
    }

    private fun setupAbout() {
        try {
            val info = packageManager.getPackageInfo(packageName, 0)
            b.tvVersion.text = "Version ${info.versionName} (${info.longVersionCode})"
        } catch (_: Exception) { }
        b.rowAbout.setOnClickListener {
            PreferenceHelper.performClickHaptic(it)
            AboutBottomSheet("https://github.com/aarshps/muthal")
                .show(supportFragmentManager, "about")
        }
    }

    private fun setupAccountButtons() {
        b.btnSignOut.setOnClickListener {
            PreferenceHelper.performClickHaptic(it)
            auth.signOut()
            finish()
        }

        b.btnDeleteAccount.setOnClickListener {
            PreferenceHelper.performHaptics(it, HapticFeedbackConstants.LONG_PRESS)
            ConfirmationBottomSheet(
                title = getString(R.string.delete_account),
                message = "This permanently deletes your institutions, entries, and account. This cannot be undone.",
                positiveButtonText = "Delete forever",
                isDestructive = true,
                onConfirm = { deleteAccount() },
            ).show(supportFragmentManager, "delete")
        }
    }

    /** PRIVACY.md promise: Settings → Delete account removes all user data + the auth record. */
    private fun deleteAccount() {
        val user = auth.currentUser ?: return
        val uid = user.uid
        val db = FirebaseFirestore.getInstance()
        lifecycleScope.launch {
            try {
                // Delete nested entries + institutions, the flat mirror, then the user doc.
                val userRef = db.collection("users").document(uid)
                val institutions = userRef.collection("institutions").get().await()
                for (inst in institutions.documents) {
                    val entries = inst.reference.collection("entries").get().await()
                    for (e in entries.documents) e.reference.delete().await()
                    inst.reference.delete().await()
                }
                val mirror = userRef.collection("entries").get().await()
                for (e in mirror.documents) e.reference.delete().await()
                userRef.delete().await()
                user.delete().await()
                Toast.makeText(this@SettingsActivity, "Account deleted", Toast.LENGTH_LONG).show()
                finish()
            } catch (e: Exception) {
                // Auth deletion can require a recent login; data is already gone by then.
                auth.signOut()
                Toast.makeText(
                    this@SettingsActivity,
                    "Data deleted. Sign in again to finish removing the account (${e.message})",
                    Toast.LENGTH_LONG,
                ).show()
                finish()
            }
        }
    }
}
