package com.doodlejoy.jarvis.core

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.doodlejoy.jarvis.JarvisApp
import com.doodlejoy.jarvis.MainActivity
import com.doodlejoy.jarvis.R

/** Fires alarms/reminders: shows a notification AND speaks the message out loud. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "JARVIS"
        val message = intent.getStringExtra(ReminderScheduler.EXTRA_MESSAGE) ?: ""

        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, JarvisApp.CHANNEL_ALERTS)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        nm.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)

        // Speak it (works when the process is alive; the notification always shows).
        try {
            JarvisApp.instance.speech.speak("মনে করিয়ে দিচ্ছি: $message")
        } catch (_: Exception) {
            // App process was killed; notification above still delivered the reminder.
        }
    }
}

/** After a reboot, alarms are wiped by Android — re-schedule every future task. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val store = ScheduleStore(context)
        store.all()
            .filter { it.timeMillis > System.currentTimeMillis() && !it.done }
            .forEach { ReminderScheduler.scheduleTask(context, it) }
    }
}
