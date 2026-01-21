package com.voicenotes.motorcycle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.Activity
import java.lang.ref.WeakReference

class FinishActivityReceiver(activity: Activity) : BroadcastReceiver() {
    private val activityRef = WeakReference(activity)
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "com.voicenotes.motorcycle.FINISH_ACTIVITY") {
            activityRef.get()?.finish()
        }
    }
}
