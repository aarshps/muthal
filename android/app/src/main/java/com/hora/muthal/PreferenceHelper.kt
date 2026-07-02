package com.hora.muthal

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.core.widget.NestedScrollView


/**
 * Muthal's app-local preference + haptics helper — the small API surface the synced
 * hora-core shared Kotlin (BaseActivity, bottom sheets, swipe/drag helpers) expects
 * every family app to provide. Mirrors the sibling apps' implementation.
 */
object PreferenceHelper {

    private const val APP_PREFS = "AppPrefs"
    private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
    private const val KEY_USE_GOOGLE_FONT = "use_google_font"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"

    /** AppCompatDelegate night-mode int (MODE_NIGHT_FOLLOW_SYSTEM default). */
    fun getThemeMode(context: Context): Int =
        context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_THEME_MODE, androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    fun setThemeMode(context: Context, mode: Int) {
        context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    fun isBiometricEnabled(context: Context): Boolean =
        context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun isHapticsEnabled(context: Context): Boolean =
        context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_HAPTICS_ENABLED, true)

    fun setHapticsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_HAPTICS_ENABLED, enabled).apply()
    }

    fun isGoogleFontEnabled(context: Context): Boolean =
        context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_USE_GOOGLE_FONT, true)

    fun setGoogleFontEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_USE_GOOGLE_FONT, enabled).apply()
    }

    /** Perform [feedbackConstant] haptic feedback if haptics are enabled. */
    fun performHaptics(view: View, feedbackConstant: Int) {
        if (isHapticsEnabled(view.context)) view.performHapticFeedback(feedbackConstant)
    }

    /** Strong "Success" / "Confirm" haptic (CONFIRM on R+, else CONTEXT_CLICK). */
    fun performSuccessHaptic(view: View) {
        if (!isHapticsEnabled(view.context)) return
        val constant = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
            HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
        view.performHapticFeedback(constant)
    }

    /** Light "Click" / "Tick" haptic. */
    fun performClickHaptic(view: View) {
        if (!isHapticsEnabled(view.context)) return
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Subtle mechanical "wheel" tick every ~40dp of NestedScrollView scroll. */
    fun attachNestedScrollHaptics(scrollView: NestedScrollView) {
        if (!isHapticsEnabled(scrollView.context)) return
        if (scrollView.getTag(R.id.haptic_scroll_listener_tag) != null) return

        var accumulatedDy = 0
        val thresholdPx = (40 * scrollView.context.resources.displayMetrics.density).toInt()
        var lastScrollY = scrollView.scrollY
        scrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { v, _, scrollY, _, _ ->
            val dy = scrollY - lastScrollY
            lastScrollY = scrollY
            accumulatedDy += dy
            if (Math.abs(accumulatedDy) >= thresholdPx) {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                accumulatedDy %= thresholdPx
            }
        })
        scrollView.setTag(R.id.haptic_scroll_listener_tag, true)
    }
}
