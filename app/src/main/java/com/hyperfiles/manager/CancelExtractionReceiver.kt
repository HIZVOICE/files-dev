package com.hyperfiles.manager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the "Cancel" action on the extraction notification. */
class CancelExtractionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ExtractionManager.ACTION_CANCEL) ExtractionManager.cancel()
    }
}
