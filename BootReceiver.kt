package com.example.tamilalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-creates all alarms after reboot, app update, or a time/timezone change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Scheduler.rescheduleAll(context)
    }
}
