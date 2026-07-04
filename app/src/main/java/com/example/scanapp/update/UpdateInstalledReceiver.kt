package com.example.scanapp.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires the moment this app's own APK finishes being replaced by an
 * update — whether that install was triggered by the auto-updater or a
 * manual "Update" tap. ACTION_MY_PACKAGE_REPLACED is a special implicit
 * broadcast: unlike most implicit broadcasts, it's still delivered to a
 * plain manifest-registered receiver (no runtime registration needed), and
 * the platform explicitly exempts it from the "no starting activities from
 * the background" restriction that applies from Android 10+ — see
 * developer.android.com/develop/background-work/services/fgs/restrictions-bg-start,
 * which lists ACTION_MY_PACKAGE_REPLACED as one of the recognized
 * exemptions. That's exactly what makes it a reliable substitute for the
 * system installer's own "Open" button: on some OEM package-installer
 * implementations, that button silently does nothing after a self-update,
 * because the app whose task/process it's trying to relaunch just got
 * replaced out from under it. This receiver relaunches the app ourselves
 * instead of depending on that button working.
 */
class UpdateInstalledReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
    }
}
