package com.zsupport.helpers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.zsupport.MainActivity

class LanguageBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        if (action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("AnyAppSupport", "BOOT_COMPLETED received, applying saved language")
            MainActivity.applySavedLanguage(context)
        }
    }
}