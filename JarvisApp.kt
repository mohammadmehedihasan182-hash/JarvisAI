package com.doodlejoy.jarvis

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.doodlejoy.jarvis.voice.SpeechManager

/**
 * Holds one shared SpeechManager (speech recognition + TTS) so Activities,
 * the wake-word service and the call receiver can all speak/listen
 * through the same engine.
 */
class JarvisApp : Application() {

    lateinit var speech: SpeechManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        speech = SpeechManager(this)
        createChannels()
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_LISTENING, "JARVIS Listening", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "JARVIS Alerts", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    companion object {
        const val CHANNEL_LISTENING = "jarvis_listening"
        const val CHANNEL_ALERTS = "jarvis_alerts"
        lateinit var instance: JarvisApp
            private set
    }
}
