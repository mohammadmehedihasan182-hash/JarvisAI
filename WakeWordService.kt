package com.doodlejoy.jarvis.voice

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.doodlejoy.jarvis.JarvisApp
import com.doodlejoy.jarvis.MainActivity
import com.doodlejoy.jarvis.R

/**
 * Wake-word service (LAST step of the build order, per spec).
 *
 * Keeps a foreground service alive with a PERMANENT notification and listens
 * in a loop. When it hears "জার্ভিস" / "jarvis" / "hey jarvis" it opens the
 * app and starts conversation mode.
 *
 * Honest notes (also in README):
 *  - Always-listening uses more battery.
 *  - On some phones set this app's battery usage to "Unrestricted".
 *  - SpeechRecognizer needs short restarts; the loop below handles that.
 */
class WakeWordService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!running) {
            running = true
            listenLoop()
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, JarvisApp.CHANNEL_LISTENING)
            .setContentTitle("JARVIS শুনছে...")
            .setContentText("\"জার্ভিস\" বললেই জেগে উঠবে")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    private fun listenLoop() {
        if (!running) return
        val speech = JarvisApp.instance.speech
        // Don't fight the TTS: while JARVIS is speaking, wait and retry.
        if (speech.isSpeaking) {
            handler.postDelayed({ listenLoop() }, 800)
            return
        }
        speech.listen(
            onResult = { heard ->
                if (isWakeWord(heard)) {
                    onWakeWord()
                }
                scheduleNext()
            },
            onError = { scheduleNext() }
        )
    }

    private fun scheduleNext() {
        handler.postDelayed({ listenLoop() }, 350)
    }

    private fun isWakeWord(text: String): Boolean {
        val t = text.lowercase()
        return t.contains("জার্ভিস") || t.contains("jarvis") || t.contains("জারবিস")
    }

    private fun onWakeWord() {
        val speech = JarvisApp.instance.speech
        speech.speak("জি, বলুন। আমি শুনছি।")
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_START_CONVERSATION, true)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
    }
}
