package com.example.tamilalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object Scheduler {
    const val ACTION_HOURLY = "com.example.tamilalarm.HOURLY"
    const val ACTION_ALARM = "com.example.tamilalarm.ALARM"
    const val EXTRA_ID = "alarm_id"

    private fun am(c: Context) = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pending(c: Context, action: String, id: Int): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            c, id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun canExact(c: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || am(c).canScheduleExactAlarms()

    /** Next top-of-the-hour announcement (id 0). */
    fun scheduleHourly(c: Context) {
        val p = pending(c, ACTION_HOURLY, 0)
        if (!Prefs.hourly(c)) {
            am(c).cancel(p)
            return
        }
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis - now < 60_000) cal.add(Calendar.HOUR_OF_DAY, 1)
        try {
            if (canExact(c)) {
                am(c).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, p)
            } else {
                am(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, p)
            }
        } catch (e: SecurityException) {
            am(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, p)
        }
    }

    /** Schedules (or cancels) a user alarm; it repeats daily until switched off. */
    fun scheduleAlarm(c: Context, a: AlarmItem) {
        val p = pending(c, ACTION_ALARM, a.id)
        if (!a.enabled) {
            am(c).cancel(p)
            return
        }
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, a.hour)
            set(Calendar.MINUTE, a.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val show = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am(c).setAlarmClock(AlarmManager.AlarmClockInfo(cal.timeInMillis, show), p)
        } catch (e: SecurityException) {
            am(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, p)
        }
    }

    fun cancelAlarm(c: Context, id: Int) {
        am(c).cancel(pending(c, ACTION_ALARM, id))
    }

    fun rescheduleAll(c: Context) {
        scheduleHourly(c)
        Prefs.alarms(c).forEach { scheduleAlarm(c, it) }
    }
}
