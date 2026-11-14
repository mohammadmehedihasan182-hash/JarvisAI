package com.doodlejoy.jarvis.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Sets exact alarms with AlarmManager:
 *  - the alarm/reminder itself,
 *  - and for schedule tasks, an EARLY reminder 15 minutes before (spec: 10–15 min আগে).
 */
object ReminderScheduler {

    const val EXTRA_TITLE = "title"
    const val EXTRA_MESSAGE = "message"
    const val EXTRA_KIND = "kind" // "alarm", "reminder", "task_early", "task"

    fun schedule(context: Context, requestCode: Int, atMillis: Long, title: String, message: String, kind: String) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context, requestCode, title, message, kind)
        if (atMillis <= System.currentTimeMillis()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            // Without exact-alarm permission, fall back to an inexact window.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    fun scheduleTask(context: Context, task: JarviTask) {
        // 15 minutes before the task:
        schedule(
            context, (task.id * 10 + 1).toInt(), task.timeMillis - 15 * 60 * 1000,
            task.title, "১৫ মিনিট পর: ${task.title}", "task_early"
        )
        // At the task time:
        schedule(
            context, (task.id * 10 + 2).toInt(), task.timeMillis,
            task.title, "এখন সময় হয়েছে: ${task.title}", "task"
        )
    }

    fun cancelTask(context: Context, taskId: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pendingIntent(context, (taskId * 10 + 1).toInt(), "", "", ""))
        am.cancel(pendingIntent(context, (taskId * 10 + 2).toInt(), "", "", ""))
    }

    private fun pendingIntent(context: Context, code: Int, title: String, message: String, kind: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_MESSAGE, message)
            putExtra(EXTRA_KIND, kind)
        }
        return PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
