package com.example.tamilalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Scheduler.ACTION_HOURLY -> {
                Scheduler.scheduleHourly(context) // line up the next hour first
                if (Prefs.hourly(context)) {
                    SpeakService.start(context, SpeakService.MODE_HOURLY)
                }
            }
            Scheduler.ACTION_ALARM -> {
                val id = intent.getIntExtra(Scheduler.EXTRA_ID, -1)
                val alarm = Prefs.alarms(context).firstOrNull { it.id == id }
                if (alarm != null && alarm.enabled) {
                    Scheduler.scheduleAlarm(context, alarm) // repeats tomorrow
                    SpeakService.start(context, SpeakService.MODE_ALARM)
                }
            }
        }
    }
}
