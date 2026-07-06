package com.example.scanapp.ui

import android.content.Context

/** The three choices exposed by the day/night selector on the homepage. */
enum class ThemeMode {
    LIGHT,
    DARK,
    /** Follow the system day/night setting — the default until the person picks otherwise. */
    AUTO
}

/** Maps a [ThemeMode] to the nullable override format ThemePreferences persists. */
fun ThemeMode.toDarkOverride(): Boolean? = when (this) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.AUTO -> null
}

/** Maps a persisted override value back to its [ThemeMode]. */
fun Boolean?.toThemeMode(): ThemeMode = when (this) {
    true -> ThemeMode.DARK
    false -> ThemeMode.LIGHT
    null -> ThemeMode.AUTO
}

/**
 * Persists the person's explicit day/night toggle choice across app
 * restarts.
 *
 * There are three effective states, represented with a nullable Boolean:
 *  - `true`  -> explicitly forced to dark
 *  - `false` -> explicitly forced to light
 *  - `null`  -> "auto": no explicit choice has been made (or it was never
 *               saved), so the app follows the system day/night setting.
 *
 * The key is simply absent from SharedPreferences in the "auto" case,
 * which is what lets [getDarkOverride] distinguish "never set" from an
 * explicit light choice — a plain `getBoolean` with a default can't tell
 * those apart on its own.
 *
 * Plain SharedPreferences rather than DataStore, matching the project's
 * existing UpdatePreferences: this is a single value read once in
 * onCreate and written once per toggle tap, so a hand-rolled store is
 * simpler than pulling in a Flow-based dependency for it.
 */
object ThemePreferences {

    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_DARK_OVERRIDE = "dark_theme_override"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Null means "auto" — follow the system day/night setting. */
    fun getDarkOverride(context: Context): Boolean? {
        val p = prefs(context)
        if (!p.contains(KEY_DARK_OVERRIDE)) return null
        return p.getBoolean(KEY_DARK_OVERRIDE, false)
    }

    /** Pass null to clear the override and go back to following the system setting. */
    fun setDarkOverride(context: Context, isDark: Boolean?) {
        val editor = prefs(context).edit()
        if (isDark == null) {
            editor.remove(KEY_DARK_OVERRIDE)
        } else {
            editor.putBoolean(KEY_DARK_OVERRIDE, isDark)
        }
        editor.apply()
    }
}
