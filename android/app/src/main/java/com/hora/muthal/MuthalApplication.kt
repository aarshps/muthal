package com.hora.muthal

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors

class MuthalApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Apply the saved light / dark / system theme before any activity is created
        // (Settings → Appearance writes this; default = follow the system).
        AppCompatDelegate.setDefaultNightMode(PreferenceHelper.getThemeMode(this))

        // Material You: derive the colour palette from the user's wallpaper on
        // Android 12+. No-op on older versions. This is the canonical Android
        // approach — every Material 3 token (colorPrimary, colorOnPrimary,
        // colorPrimaryContainer, etc.) is filled in by the system at runtime.
        // Family standard — see hora-core docs/conventions.md.
        DynamicColors.applyToActivitiesIfAvailable(this)

        // Create the notification channel for transaction alerts per Hora standard
        NotificationHelper.createNotificationChannel(this)

        // Resource-shrinker keep-alive for Theme.Muthal.SystemFont (Settings → Appearance
        // → Google Sans font toggle). It's only ever looked up dynamically
        // (BaseActivity builds the name from a runtime string via
        // Resources.getIdentifier()), so R8's resource shrinker can't trace it as
        // reachable and strips it from release builds — confirmed with `aapt2 dump
        // resources app-release.apk`: present in debug, absent from release. Neither
        // `res/raw/keep.xml`'s `tools:keep` nor an *unused* private field reference
        // survived R8 on this project's AGP version — R8's own dead-code elimination
        // removes an unread private field before the resource shrinker's reachability
        // scan ever sees it (confirmed via app/build/outputs/mapping/release/resources.txt:
        // it showed "reachable from Field ..." and "is not reachable" for the SAME build).
        // A real Log call is a genuine side effect R8 won't strip, so the resource id it
        // references survives too. This log line is expected to be a permanent no-op
        // (v never equals a negative/zero id) — its only job is keeping the reference.
        val systemFontThemeId = R.style.Theme_Muthal_SystemFont
        if (systemFontThemeId == 0) Log.w("Muthal", "Theme.Muthal.SystemFont missing from build")
    }
}
