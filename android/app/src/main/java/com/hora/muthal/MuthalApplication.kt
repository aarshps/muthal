package com.hora.muthal

import android.app.Application
import com.google.android.material.color.DynamicColors

class MuthalApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Material You: derive the colour palette from the user's wallpaper on
        // Android 12+. No-op on older versions. This is the canonical Android
        // approach — every Material 3 token (colorPrimary, colorOnPrimary,
        // colorPrimaryContainer, etc.) is filled in by the system at runtime.
        // Family standard — see hora-core docs/conventions.md.
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
