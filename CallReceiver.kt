package com.doodlejoy.jarvis.call

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.CallLog
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import com.doodlejoy.jarvis.JarvisApp
import com.doodlejoy.jarvis.MainActivity
import com.doodlejoy.jarvis.R
import com.doodlejoy.jarvis.core.CallSmsHelper

/**
 * Call management (spec section 5):
 *  - Incoming call: announce WHO is calling out loud ("রহিম ফোন করেছেন, ধরবেন?")
 *  - Missed call: notification reminder + spoken reminder when the app runs.
 *
 * Note: Android does not let a normal app answer/decline calls by voice —
 * JARVIS announces and asks, the user taps the phone's own answer button.
 * (Talking TO the caller as AI is excluded for now, per spec.)
 */
class CallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (!number.isNullOrBlank()) {
                    lastRingingNumber = number
                    val name = CallSmsHelper.nameForNumber(context, number)
                    val who = name ?: number
                    speak(context, "$who ফোন করেছেন। ধরবেন?")
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                // If it rang and was never picked up (last state was RINGING), it is a missed call.
                val missed = lastRingingNumber
                if (missed != null && wasRinging) {
                    lastRingingNumber = null
                    notifyMissed(context, missed)
                }
                wasRinging = false
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                wasRinging = false
                lastRingingNumber = null
            }
        }
        if (state == TelephonyManager.EXTRA_STATE_RINGING) wasRinging = true
    }

    private fun notifyMissed(context: Context, number: String) {
        val name = CallSmsHelper.nameForNumber(context, number)
        val who = name ?: number
        val text = "আপনার একটি মিসড কল আছে: $who"
        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, JarvisApp.CHANNEL_ALERTS)
            .setContentTitle("মিসড কল")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        context.getSystemService(android.app.NotificationManager::class.java)
            .notify(2001, notification)
        speak(context, text)
    }

    private fun speak(context: Context, text: String) {
        try {
            (context.applicationContext as JarvisApp).speech.speak(text)
        } catch (_: Exception) {
            // Process/TTS not ready — the system call UI still shows the call normally.
        }
    }

    companion object {
        @Volatile private var lastRingingNumber: String? = null
        @Volatile private var wasRinging: Boolean = false

        /** Latest missed call from the system call log (for "মিসড কল আছে?" questions). */
        fun latestMissedCall(context: Context): String? {
            return try {
                context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME),
                    CallLog.Calls.TYPE + " = ?",
                    arrayOf(CallLog.Calls.MISSED_TYPE.toString()),
                    CallLog.Calls.DATE + " DESC"
                )?.use { c ->
                    if (c.moveToFirst()) c.getString(1) ?: c.getString(0) else null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
