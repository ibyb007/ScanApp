package com.example.scanapp.ui

import android.content.Context

/**
 * Persists the person's chosen opacity for the "liquid glass" bottom
 * navigation bar (see [ScanAppBottomNav]) across app restarts.
 *
 * Plain SharedPreferences, matching the project's existing ThemePreferences
 * / UpdatePreferences: this is a single value read once in onCreate and
 * written whenever the person drags the slider on the Settings screen, so a
 * hand-rolled store is simpler than pulling in a Flow-based dependency for
 * it.
 */
object NavBarPreferences {

    private const val PREFS_NAME = "navbar_prefs"
    private const val KEY_GLASS_OPACITY = "glass_opacity"

    /** Opacity used the very first time the app runs, before any explicit choice is saved. */
    const val DEFAULT_GLASS_OPACITY = 0.55f

    /** The bar never goes fully invisible or fully opaque — both ends still read as "glass". */
    const val MIN_GLASS_OPACITY = 0.15f
    const val MAX_GLASS_OPACITY = 1f

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getGlassOpacity(context: Context): Float {
        val stored = prefs(context).getFloat(KEY_GLASS_OPACITY, DEFAULT_GLASS_OPACITY)
        return stored.coerceIn(MIN_GLASS_OPACITY, MAX_GLASS_OPACITY)
    }

    fun setGlassOpacity(context: Context, opacity: Float) {
        prefs(context).edit()
            .putFloat(KEY_GLASS_OPACITY, opacity.coerceIn(MIN_GLASS_OPACITY, MAX_GLASS_OPACITY))
            .apply()
    }
}
