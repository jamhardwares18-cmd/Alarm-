package com.example.tamilalarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.Locale

/**
 * Speaks the time in Tamil (and then the Bible verse for that hour).
 * HOURLY mode: speak once and stop.
 * ALARM mode: ring + speak the time, repeat up to 10 times or until "நிறுத்து" is tapped.
 */
class SpeakService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_HOURLY = "hourly"
        const val MODE_ALARM = "alarm"
        const val ACTION_STOP = "com.example.tamilalarm.STOP"
        private const val CHANNEL = "tamil_alarm"
        private const val NOTIF_ID = 42
        private const val MAX_ALARM_CYCLES = 10

        fun start(c: Context, mode: String) {
            ContextCompat.startForegroundService(
                c, Intent(c, SpeakService::class.java).putExtra(EXTRA_MODE, mode)
            )
        }
    }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var langOk = false
    private var mode = MODE_HOURLY
    private var cycle = 0
    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_HOURLY
        cycle = 0
        handler.removeCallbacksAndMessages(null)
        stopRingtone()

        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )

        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tamilalarm:speak")
                .apply { acquire(10 * 60 * 1000L) }
        }

        if (tts == null) {
            tts = TextToSpeech(this, this)
        } else if (ttsReady) {
            beginCycle()
        }
        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        val t = tts ?: return
        if (status == TextToSpeech.SUCCESS) {
            val r = t.setLanguage(Locale("ta", "IN"))
            langOk = r >= TextToSpeech.LANG_AVAILABLE
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == "last") handler.post { onSpeechFinished() }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    handler.post { onSpeechFinished() }
                }
            })
        } else {
            langOk = false
        }
        ttsReady = true
        beginCycle()
    }

    private fun beginCycle() {
        requestFocus()
        if (mode == MODE_ALARM) {
            playRingtone(RingtoneManager.TYPE_ALARM)
            handler.postDelayed({
                stopRingtone()
                speakNow()
            }, 5000)
        } else {
            speakNow()
        }
    }

    private fun speakNow() {
        val t = tts
        if (t == null || !langOk) {
            // Tamil voice missing: just beep so the user still notices.
            if (mode == MODE_HOURLY) {
                playRingtone(RingtoneManager.TYPE_NOTIFICATION)
                handler.postDelayed({
                    stopRingtone()
                    onSpeechFinished()
                }, 3000)
            } else {
                onSpeechFinished()
            }
            return
        }

        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val timeText = if (mode == MODE_ALARM) {
            TamilTime.alarm(hour, now.get(Calendar.MINUTE))
        } else {
            TamilTime.hourly(hour)
        }
        // In alarm mode the verse is read only on the first round.
        val verse = if (mode == MODE_ALARM && cycle > 0) null else Verses.speech(this, hour)

        val usage = if (mode == MODE_ALARM) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA
        t.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )

        if (verse.isNullOrBlank()) {
            t.speak(timeText, TextToSpeech.QUEUE_FLUSH, null, "last")
        } else {
            t.speak(timeText, TextToSpeech.QUEUE_FLUSH, null, "time")
            t.playSilentUtterance(700, TextToSpeech.QUEUE_ADD, "gap")
            t.speak(verse, TextToSpeech.QUEUE_ADD, null, "last")
        }
    }

    private fun onSpeechFinished() {
        if (mode == MODE_ALARM) {
            cycle++
            if (cycle >= MAX_ALARM_CYCLES) {
                shutdown()
            } else {
                handler.postDelayed({ beginCycle() }, 4000)
            }
        } else {
            shutdown()
        }
    }

    private fun playRingtone(type: Int) {
        stopRingtone()
        try {
            val uri = RingtoneManager.getDefaultUri(type)
            val r = RingtoneManager.getRingtone(this, uri) ?: return
            r.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(
                        if (type == RingtoneManager.TYPE_ALARM) AudioAttributes.USAGE_ALARM
                        else AudioAttributes.USAGE_NOTIFICATION
                    )
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            r.play()
            ringtone = r
        } catch (e: Exception) {
            // ignore: speech still works
        }
    }

    private fun stopRingtone() {
        try {
            ringtone?.stop()
        } catch (e: Exception) {
        }
        ringtone = null
    }

    @Suppress("DEPRECATION")
    private fun requestFocus() {
        try {
            (getSystemService(Context.AUDIO_SERVICE) as AudioManager).requestAudioFocus(
                null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        } catch (e: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun abandonFocus() {
        try {
            (getSystemService(Context.AUDIO_SERVICE) as AudioManager).abandonAudioFocus(null)
        } catch (e: Exception) {
        }
    }

    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        stopRingtone()
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        abandonFocus()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun shutdown() {
        cleanup()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun buildNotification(): android.app.Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Tamil Alarm", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val stop = PendingIntent.getService(
            this, 1, Intent(this, SpeakService::class.java).setAction(ACTION_STOP), flags
        )
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), flags)
        val title = if (mode == MODE_ALARM) "அலாரம்" else "நேர அறிவிப்பு"
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText("நிறுத்த 'நிறுத்து' தட்டவும்")
            .setContentIntent(open)
            .addAction(0, "நிறுத்து", stop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
