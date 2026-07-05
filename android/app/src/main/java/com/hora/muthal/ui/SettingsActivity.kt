package com.hora.muthal.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
        setupFontToggle()
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
        // Chip must be checked BEFORE the first styleChipGroup() call — ChipHelper reads
        // chip.isChecked synchronously to decide filled-vs-outlined styling, so styling
        // the group first (while every chip is still unchecked) left the selected pill
        // looking unselected until the user tapped a chip and the listener re-styled it.
        when (PreferenceHelper.getThemeMode(this)) {
            AppCompatDelegate.MODE_NIGHT_NO -> b.chipThemeLight.isChecked = true
            AppCompatDelegate.MODE_NIGHT_YES -> b.chipThemeDark.isChecked = true
            else -> b.chipThemeSystem.isChecked = true
        }
        ChipHelper.styleChipGroup(b.themeChips)
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

    /** Google Sans (brand, rounded) vs the device's system font — same preference the
     * web Settings screen exposes as "Rounded font" (Pathivu/Varisankya Android parity). */
    private fun setupFontToggle() {
        b.switchGoogleFont.isChecked = PreferenceHelper.isGoogleFontEnabled(this)
        b.switchGoogleFont.setOnCheckedChangeListener { view, checked ->
            PreferenceHelper.performClickHaptic(view)
            PreferenceHelper.setGoogleFontEnabled(this, checked)
            recreate()
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

    private fun showLoading(message: String) {
        b.loadingOverlay.visibility = View.VISIBLE
        b.textLoadingMessage.text = message
    }

    private fun hideLoading() {
        b.loadingOverlay.visibility = View.GONE
    }

    /** PRIVACY.md promise: Settings → Delete account removes all user data + the auth
     * record. Institutions are now shared (SPEC §1) — this only removes the user's OWN
     * presence (their member doc in each institution + their memberships index), never
     * shared entries/categories other members depend on. If the user was the sole owner
     * of an institution, it is left ownerless per SPEC §3 (acceptable at this scale). */
    private fun deleteAccount() {
        val user = auth.currentUser ?: return
        val uid = user.uid
        val db = FirebaseFirestore.getInstance()
        showLoading("Deleting your account data...")
        lifecycleScope.launch {
            try {
                val userRef = db.collection("users").document(uid)
                val memberships = userRef.collection("memberships").get().await()
                coroutineScope {
                    val jobs = memberships.documents.map { m ->
                        async {
                            try {
                                db.collection("institutions").document(m.id)
                                    .collection("members").document(uid).delete().await()
                            } catch (e: Exception) {
                                // Ignore if document doesn't exist
                            }
                            m.reference.delete().await()
                        }
                    }
                    jobs.awaitAll()
                }
                userRef.delete().await()
                user.delete().await()
                hideLoading()
                Toast.makeText(this@SettingsActivity, "Account deleted", Toast.LENGTH_LONG).show()
                finish()
            } catch (e: Exception) {
                hideLoading()
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
