package com.doodlejoy.jarvis.ui

import android.app.TimePickerDialog
import android.content.ContentUris
import android.os.Bundle
import android.provider.CalendarContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.doodlejoy.jarvis.JarvisApp
import com.doodlejoy.jarvis.core.JarviTask
import com.doodlejoy.jarvis.core.Prefs
import com.doodlejoy.jarvis.core.ReminderScheduler
import com.doodlejoy.jarvis.core.ScheduleStore
import com.doodlejoy.jarvis.databinding.FragmentScheduleBinding
import com.doodlejoy.jarvis.databinding.ItemTaskBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Schedule tab (spec section 4):
 *  - JARVIS's own tasks for today (add by typing, or by voice from Home/Chat)
 *  - reads the phone CALENDAR for today's events
 *  - morning summary button, tomorrow's draft button
 *  - every task automatically gets a reminder 15 minutes early (ReminderScheduler)
 */
class ScheduleFragment : Fragment() {

    private var _binding: FragmentScheduleBinding? = null
    private val binding get() = _binding!!
    private lateinit var store: ScheduleStore
    private lateinit var prefs: Prefs
    private val adapter = TaskAdapter(mutableListOf()) { task ->
        ReminderScheduler.cancelTask(requireContext(), task.id)
        store.delete(task.id)
        reload()
    }
    private var pickedTime: Calendar? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        store = ScheduleStore(requireContext())
        prefs = Prefs(requireContext())
        binding.taskList.layoutManager = LinearLayoutManager(requireContext())
        binding.taskList.adapter = adapter
        reload()

        binding.taskTime.setOnClickListener {
            val now = Calendar.getInstance()
            TimePickerDialog(requireContext(), { _, h, m ->
                pickedTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, h)
                    set(Calendar.MINUTE, m)
                    set(Calendar.SECOND, 0)
                }
                binding.taskTime.setText(SimpleDateFormat("hh:mm a", Locale.US).format(pickedTime!!.time))
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show()
        }

        binding.addTask.setOnClickListener {
            val title = binding.taskTitle.text?.toString()?.trim().orEmpty()
            val time = pickedTime
            if (title.isEmpty() || time == null) {
                Toast.makeText(requireContext(), "কাজের নাম আর সময় দিন", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val task = store.add(title, time.timeInMillis)
            ReminderScheduler.scheduleTask(requireContext(), task)
            binding.taskTitle.text?.clear()
            binding.taskTime.text?.clear()
            pickedTime = null
            reload()
            JarvisApp.instance.speech.speak("যোগ করলাম: $title")
        }

        binding.morningSummary.setOnClickListener { speakSummary(today = true) }
        binding.tomorrowPlan.setOnClickListener { speakSummary(today = false) }
    }

    private fun reload() {
        adapter.replace(store.tasksToday())
    }

    /** Morning summary / tomorrow draft, spoken out loud. Includes calendar events. */
    private fun speakSummary(today: Boolean) {
        val tasks = if (today) store.tasksToday() else store.tasksTomorrow()
        val events = readCalendarEvents(today)
        val bn = prefs.language != "en"
        val sb = StringBuilder()
        sb.append(
            when {
                today && bn -> "আজকের সারাংশ। "
                today -> "Your summary for today. "
                bn -> "আগামীকালের খসড়া। "
                else -> "Tomorrow's draft. "
            }
        )
        if (tasks.isEmpty() && events.isEmpty()) {
            sb.append(if (bn) "কোনো কাজ নেই।" else "Nothing scheduled.")
        }
        tasks.forEach { sb.append("${it.timeText()}-এ ${it.title}। ") }
        events.forEach { sb.append("${it.second}-এ ক্যালেন্ডারে: ${it.first}। ") }
        JarvisApp.instance.speech.speak(sb.toString())
        Toast.makeText(requireContext(), sb.toString(), Toast.LENGTH_LONG).show()
    }

    /** Read today's (or tomorrow's) events from the system calendar. Returns (title, timeText). */
    private fun readCalendarEvents(today: Boolean): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        try {
            val dayStart = Calendar.getInstance().apply {
                if (!today) add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }
            val begin = dayStart.timeInMillis
            val end = begin + 24L * 60 * 60 * 1000
            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, begin)
            ContentUris.appendId(builder, end)
            requireContext().contentResolver.query(
                builder.build(),
                arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN),
                null, null, CalendarContract.Instances.BEGIN + " ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val title = c.getString(0) ?: continue
                    val start = c.getLong(1)
                    out.add(title to SimpleDateFormat("hh:mm a", Locale.US).format(java.util.Date(start)))
                }
            }
        } catch (e: SecurityException) {
            // No READ_CALENDAR permission — the summary just skips calendar events.
        }
        return out
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class TaskAdapter(
    private val items: MutableList<JarviTask>,
    private val onDelete: (JarviTask) -> Unit
) : RecyclerView.Adapter<TaskAdapter.VH>() {

    class VH(val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root)

    fun replace(tasks: List<JarviTask>) {
        items.clear()
        items.addAll(tasks)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val task = items[position]
        holder.binding.taskText.text = "${task.timeText()}  •  ${task.title}"
        holder.binding.taskDelete.setOnClickListener { onDelete(task) }
    }

    override fun getItemCount() = items.size
}
