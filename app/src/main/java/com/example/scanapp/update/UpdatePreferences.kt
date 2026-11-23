package com.example.scanapp.update

import android.content.Context
import com.example.scanapp.BuildConfig

/**
 * Persists the update-related settings (two toggles + the update channel) across app restarts.
 *
 * Plain SharedPreferences rather than DataStore — this is two booleans with
 * no need for Flow-based reactive observation (Settings reads them once on
 * screen entry, MainActivity reads them once in onCreate), so pulling in the
 * DataStore dependency for this would be more machinery than the feature
 * needs. Matches the project's existing style of avoiding new dependencies
 * where a small hand-rolled approach is just as reliable (see UpdateChecker's
 * own dependency-free JSON field extraction).
 */
object UpdatePreferences {

    private const val PREFS_NAME = "update_prefs"
    private const val KEY_CHECK_ON_START = "check_updates_on_start"
    private const val KEY_AUTO_INSTALL = "auto_install_updates"
    private const val KEY_UPDATE_CHANNEL = "update_channel"

    // Checking for updates on every launch is a reasonable, low-friction
    // default; auto-install defaults OFF since it's the more consequential
    // of the two (downloads + prompts to install something without an
    // explicit per-update tap on the Settings screen).
    private const val DEFAULT_CHECK_ON_START = true
    private const val DEFAULT_AUTO_INSTALL = false

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isCheckOnStartEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CHECK_ON_START, DEFAULT_CHECK_ON_START)

    fun setCheckOnStartEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CHECK_ON_START, enabled).apply()
    }

    fun isAutoInstallEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_INSTALL, DEFAULT_AUTO_INSTALL)

    fun setAutoInstallEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_INSTALL, enabled).apply()
    }

    /**
     * Saved update channel. On first use a canary build starts on Canary and
     * anything else on Stable, so a tester isn't silently moved off canary.
     */
    fun getUpdateChannel(context: Context): UpdateChannel {
        val saved = UpdateChannel.fromStoredValue(prefs(context).getString(KEY_UPDATE_CHANNEL, null))
        if (saved != null) return saved
        val default = UpdateChannel.defaultForVersion(BuildConfig.VERSION_NAME)
        prefs(context).edit().putString(KEY_UPDATE_CHANNEL, default.storedValue).apply()
        return default
    }

    fun setUpdateChannel(context: Context, channel: UpdateChannel) {
        prefs(context).edit().putString(KEY_UPDATE_CHANNEL, channel.storedValue).apply()
    }
}
