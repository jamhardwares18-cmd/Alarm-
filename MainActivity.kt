package com.example.tamilalarm

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var alarmList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        alarmList = findViewById(R.id.alarmList)

        val hourlySwitch = findViewById<Switch>(R.id.hourlySwitch)
        hourlySwitch.isChecked = Prefs.hourly(this)
        hourlySwitch.setOnCheckedChangeListener { _, on ->
            Prefs.setHourly(this, on)
            Scheduler.scheduleHourly(this)
        }

        findViewById<Button>(R.id.testBtn).setOnClickListener {
            SpeakService.start(this, SpeakService.MODE_HOURLY)
        }
        findViewById<Button>(R.id.versesBtn).setOnClickListener {
            startActivity(Intent(this, VersesActivity::class.java))
        }
        findViewById<Button>(R.id.addAlarmBtn).setOnClickListener { pickTime() }
        statusText.setOnClickListener { openTtsSettings() }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        askExactAlarmIfNeeded()

        Scheduler.rescheduleAll(this)
        renderAlarms()
    }

    override fun onResume() {
        super.onResume()
        checkTts()
    }

    private fun askExactAlarmIfNeeded() {
        if (Build.VERSION.SDK_INT in 31..32) {
            val am = getSystemService(AlarmManager::class.java)
            if (!am.canScheduleExactAlarms()) {
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:$packageName")
                        )
                    )
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun checkTts() {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(this) { status ->
            val msg = if (status == TextToSpeech.SUCCESS) {
                val r = engine?.isLanguageAvailable(Locale("ta", "IN")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                if (r >= TextToSpeech.LANG_AVAILABLE) {
                    "தமிழ் குரல் தயார் ✓"
                } else {
                    "⚠ தமிழ் குரல் இல்லை. இங்கே தட்டி, Google Text-to-speech-ல் Tamil voice data install செய்யவும்."
                }
            } else {
                "⚠ Text-to-speech இயங்கவில்லை. இங்கே தட்டி settings பார்க்கவும்."
            }
            statusText.text = msg
            engine?.shutdown()
        }
    }

    private fun openTtsSettings() {
        try {
            startActivity(Intent("com.android.settings.TTS_SETTINGS"))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e2: Exception) {
            }
        }
    }

    private fun pickTime() {
        val c = Calendar.getInstance()
        TimePickerDialog(
            this,
            TimePickerDialog.OnTimeSetListener { _, h, m ->
                val item = AlarmItem(Prefs.nextId(this), h, m, true)
                Prefs.saveAlarms(this, Prefs.alarms(this) + item)
                Scheduler.scheduleAlarm(this, item)
                renderAlarms()
                Toast.makeText(this, "அலாரம் அமைக்கப்பட்டது: ${label(item)}", Toast.LENGTH_SHORT).show()
            },
            c.get(Calendar.HOUR_OF_DAY),
            c.get(Calendar.MINUTE),
            false
        ).show()
    }

    private fun label(a: AlarmItem): String {
        val h12 = if (a.hour % 12 == 0) 12 else a.hour % 12
        return "${TamilTime.period(a.hour)} $h12:${"%02d".format(a.minute)}"
    }

    private fun renderAlarms() {
        alarmList.removeAllViews()
        val list = Prefs.alarms(this).sortedWith(compareBy({ it.hour }, { it.minute }))
        if (list.isEmpty()) {
            alarmList.addView(TextView(this).apply { setText(R.string.no_alarms) })
            return
        }
        for (a in list) {
            val row = layoutInflater.inflate(R.layout.item_alarm, alarmList, false)
            row.findViewById<TextView>(R.id.timeText).text = label(a)
            val sw = row.findViewById<Switch>(R.id.alarmSwitch)
            sw.isChecked = a.enabled
            sw.setOnCheckedChangeListener { _, on ->
                val updated = a.copy(enabled = on)
                Prefs.updateAlarm(this, updated)
                Scheduler.scheduleAlarm(this, updated)
            }
            row.findViewById<Button>(R.id.deleteBtn).setOnClickListener {
                Scheduler.cancelAlarm(this, a.id)
                Prefs.removeAlarm(this, a.id)
                renderAlarms()
            }
            alarmList.addView(row)
        }
    }
}
