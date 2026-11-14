package com.doodlejoy.jarvis.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** One scheduled task in JARVIS's own schedule. */
data class JarviTask(
    val id: Long,
    val title: String,
    val timeMillis: Long,
    val done: Boolean = false
) {
    fun timeText(): String =
        SimpleDateFormat("hh:mm a", Locale.US).format(java.util.Date(timeMillis))

    fun dayText(): String =
        SimpleDateFormat("EEE, d MMM", Locale.US).format(java.util.Date(timeMillis))
}

/**
 * Schedule storage (spec section 4).
 * Tasks live in SharedPreferences as JSON — simple and dependency-free.
 * Calendar events are read separately from the system calendar (see ScheduleFragment).
 */
class ScheduleStore(context: Context) {

    private val sp = context.getSharedPreferences("jarvis_tasks", Context.MODE_PRIVATE)

    fun all(): List<JarviTask> {
        val raw = sp.getString(KEY_TASKS, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = mutableListOf<JarviTask>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                JarviTask(
                    id = o.getLong("id"),
                    title = o.getString("title"),
                    timeMillis = o.getLong("time"),
                    done = o.optBoolean("done", false)
                )
            )
        }
        return out.sortedBy { it.timeMillis }
    }

    fun add(title: String, timeMillis: Long): JarviTask {
        val task = JarviTask(System.currentTimeMillis(), title, timeMillis)
        save(all() + task)
        return task
    }

    fun delete(id: Long) {
        save(all().filterNot { it.id == id })
    }

    fun tasksOn(dayStartMillis: Long): List<JarviTask> {
        val end = dayStartMillis + 24L * 60 * 60 * 1000
        return all().filter { it.timeMillis in dayStartMillis until end }
    }

    fun tasksToday(): List<JarviTask> = tasksOn(startOfToday())

    fun tasksTomorrow(): List<JarviTask> =
        tasksOn(startOfToday() + 24L * 60 * 60 * 1000)

    private fun save(tasks: List<JarviTask>) {
        val arr = JSONArray()
        tasks.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("title", t.title)
                    .put("time", t.timeMillis)
                    .put("done", t.done)
            )
        }
        sp.edit().putString(KEY_TASKS, arr.toString()).apply()
    }

    companion object {
        private const val KEY_TASKS = "tasks"

        fun startOfToday(): Long = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
