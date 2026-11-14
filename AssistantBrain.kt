package com.doodlejoy.jarvis.core

import android.content.Context
import com.doodlejoy.jarvis.call.CallReceiver
import java.util.Calendar

/**
 * The command brain (spec sections 2–5).
 * Understands Bengali + English commands and returns one of:
 *  - Reply:            just say it.
 *  - AskConfirmation:  say the question and WAIT. The pending action runs only
 *                      if the very next thing the user says is "হ্যাঁ"/"yes".
 *                      (Security rule: sensitive work always confirms first.)
 *  - NeedsAi:          not a built-in command — send to AiChatClient (server AI).
 *
 * JARVIS does nothing the user did not command (spec section 6).
 */
class AssistantBrain(private val context: Context) {

    private val prefs = Prefs(context)
    private val store = ScheduleStore(context)
    private var pendingAction: (() -> String)? = null

    sealed class Outcome {
        data class Reply(val text: String) : Outcome()
        data class AskConfirmation(val question: String) : Outcome()
        object NeedsAi : Outcome()
    }

    private val bn get() = prefs.language != "en"

    fun handle(rawInput: String): Outcome {
        val input = rawInput.trim()
        val lower = input.lowercase()
        val norm = toEnglishDigits(lower)

        // 1) A confirmation is pending? Only yes/no matters now.
        if (pendingAction != null) {
            return when {
                isYes(lower) -> {
                    val action = pendingAction!!
                    pendingAction = null
                    Outcome.Reply(action())
                }
                isNo(lower) -> {
                    pendingAction = null
                    Outcome.Reply(if (bn) "ঠিক আছে, বাতিল করে দিলাম।" else "Okay, cancelled.")
                }
                else -> {
                    pendingAction = null
                    handleFresh(norm, lower, input) // treat as a new command
                }
            }
        }
        return handleFresh(norm, lower, input)
    }

    private fun handleFresh(norm: String, lower: String, original: String): Outcome {
        // ---- Greetings / identity ----
        if (lower.containsAny("আসসালামু", "হ্যালো", "hello", "hi jarvis", "হাই")) {
            val name = prefs.userName
            return Outcome.Reply(
                if (bn) "ওয়ালাইকুম আসসালাম${if (name.isNotEmpty()) " $name" else ""}! আমি জার্ভিস। কী করতে পারি?"
                else "Hello${if (name.isNotEmpty()) " $name" else ""}! I am JARVIS. How can I help?"
            )
        }
        if (lower.containsAny("তুমি কে", "তোমার নাম", "who are you", "your name")) {
            return Outcome.Reply(if (bn) "আমি জার্ভিস, আপনার ভয়েস অ্যাসিস্ট্যান্ট।" else "I am JARVIS, your voice assistant.")
        }

        // ---- Remember my name ----
        extractAfter(original, listOf("আমার নাম", "আমি "))?.let { name ->
            if (lower.contains("নাম")) {
                prefs.userName = name.trim()
                return Outcome.Reply(if (bn) "মনে রাখলাম। আপনার নাম ${prefs.userName}।" else "I will remember that, ${prefs.userName}.")
            }
        }

        // ---- Phone info ----
        if (lower.containsAny("ব্যাটারি", "battery", "চার্জ কত")) {
            val p = PhoneInfo.batteryPercent(context)
            return Outcome.Reply(if (bn) "ব্যাটারি $p%${if (PhoneInfo.isCharging(context)) ", চার্জ হচ্ছে" else ""}।" else "Battery is $p%.")
        }
        if (lower.containsAny("স্টোরেজ", "storage")) {
            return Outcome.Reply((if (bn) "স্টোরেজ: " else "Storage: ") + PhoneInfo.storageText())
        }
        if (lower.containsAny("সময় কত", "কয়টা বাজে", "what time", "the time")) {
            return Outcome.Reply((if (bn) "এখন সময় " else "It is ") + PhoneInfo.nowText(bn))
        }
        if (lower.containsAny("ফোনের তথ্য", "phone info", "device info")) {
            return Outcome.Reply(PhoneInfo.summary(context, bn))
        }

        // ---- Alarm: "সকাল ৭টায় অ্যালার্ম দাও" / "set alarm at 7" ----
        if (lower.containsAny("অ্যালার্ম", "এলার্ম", "alarm")) {
            val t = parseTime(norm) ?: return Outcome.Reply(
                if (bn) "কোন সময়ে অ্যালার্ম দেব? যেমন বলুন: সকাল ৭টায় অ্যালার্ম দাও।" else "At what time? Say: set alarm at 7 AM."
            )
            val at = nextOccurrence(t.first, t.second)
            ReminderScheduler.schedule(context, (at % Int.MAX_VALUE).toInt(), at, "অ্যালার্ম", "অ্যালার্ম বাজছে!", "alarm")
            return Outcome.Reply(if (bn) "অ্যালার্ম সেট করে দিলাম: ${formatTime(t.first, t.second)}।" else "Alarm set for ${formatTime(t.first, t.second)}.")
        }

        // ---- Reminder in X minutes: "১০ মিনিট পর ওষুধ খেতে মনে করিয়ে দাও" ----
        if (lower.containsAny("মনে করিয়ে", "রিমাইন্ডার", "remind")) {
            val minutes = Regex("(\\d+)\\s*(মিনিট|minute|min)").find(norm)?.groupValues?.get(1)?.toIntOrNull()
            val hours = Regex("(\\d+)\\s*(ঘণ্টা|hour)").find(norm)?.groupValues?.get(1)?.toIntOrNull()
            val delayMin = (hours ?: 0) * 60 + (minutes ?: 0)
            val what = original
                .replace(Regex("(?i)(মনে করিয়ে দাও|মনে করিয়ে|রিমাইন্ডার|remind me|remind)"), "")
                .replace(Regex("(\\d+\\s*(মিনিট|ঘণ্টা|minute|hour|min)\\s*(পর|পরে|after)?)"), "")
                .trim().ifBlank { "রিমাইন্ডার" }
            if (delayMin > 0) {
                val at = System.currentTimeMillis() + delayMin * 60_000L
                ReminderScheduler.schedule(context, (at % Int.MAX_VALUE).toInt(), at, "রিমাইন্ডার", what, "reminder")
                return Outcome.Reply(if (bn) "$delayMin মিনিট পর মনে করিয়ে দেব: $what।" else "I will remind you in $delayMin minutes: $what.")
            }
            val t = parseTime(norm)
            if (t != null) {
                val at = nextOccurrence(t.first, t.second)
                ReminderScheduler.schedule(context, (at % Int.MAX_VALUE).toInt(), at, "রিমাইন্ডার", what, "reminder")
                return Outcome.Reply(if (bn) "${formatTime(t.first, t.second)}-এ মনে করিয়ে দেব: $what।" else "Reminder set for ${formatTime(t.first, t.second)}: $what.")
            }
            return Outcome.Reply(if (bn) "কখন মনে করিয়ে দেব? যেমন: ১০ মিনিট পর ওষুধ খেতে মনে করিয়ে দাও।" else "When should I remind you? Say: remind me in 10 minutes.")
        }

        // ---- Add to schedule: "কাল সকাল ৯টায় মিটিং যোগ করো" ----
        if (lower.containsAny("যোগ কর", "শিডিউলে রাখ", "add task", "schedule")) {
            val t = parseTime(norm) ?: return Outcome.Reply(
                if (bn) "সময়টা বলুন, যেমন: কাল সকাল ৯টায় মিটিং যোগ করো।" else "Tell me the time, e.g. add meeting tomorrow at 9 AM."
            )
            val title = original
                .replace(Regex("(?i)(যোগ করো|যোগ কর|শিডিউলে রাখো|add|schedule)"), "")
                .replace(Regex("(কাল|আগামীকাল|tomorrow)"), "")
                .replace(Regex("\\d{1,2}(:\\d{2})?\\s*(টায়|টাই|am|pm|সকাল|দুপুর|বিকেল|সন্ধ্যা|রাত)?"), "")
                .trim().ifBlank { "কাজ" }
            val cal = Calendar.getInstance().apply {
                if (lower.containsAny("কাল", "আগামীকাল", "tomorrow")) add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, t.first)
                set(Calendar.MINUTE, t.second)
                set(Calendar.SECOND, 0)
            }
            val task = store.add(title, cal.timeInMillis)
            ReminderScheduler.scheduleTask(context, task) // includes the 15-minute-early reminder
            return Outcome.Reply(if (bn) "শিডিউলে যোগ করলাম: $title, ${task.dayText()} ${task.timeText()}। ১৫ মিনিট আগেও মনে করিয়ে দেব।" else "Added: $title, ${task.dayText()} ${task.timeText()}. I will also remind you 15 minutes before.")
        }

        // ---- Read schedule ----
        if (lower.containsAny("আজকের কাজ", "আজ কি আছে", "আজকের শিডিউল", "today", "সকালের সারাংশ", "সুপ্রভাত", "good morning")) {
            val tasks = store.tasksToday()
            return Outcome.Reply(
                if (tasks.isEmpty()) {
                    if (bn) "আজকের শিডিউলে কোনো কাজ নেই।" else "You have nothing scheduled today."
                } else {
                    val list = tasks.joinToString("। ") { "${it.timeText()}-এ ${it.title}" }
                    if (bn) "আজকের কাজ ${tasks.size}টি: $list।" else "Today you have ${tasks.size} tasks: $list."
                }
            )
        }
        if (lower.containsAny("আগামীকাল", "কালকের", "tomorrow")) {
            val tasks = store.tasksTomorrow()
            return Outcome.Reply(
                if (tasks.isEmpty()) {
                    if (bn) "আগামীকালের খসড়ায় এখনো কোনো কাজ নেই।" else "Tomorrow's plan is empty so far."
                } else {
                    val list = tasks.joinToString("। ") { "${it.timeText()}-এ ${it.title}" }
                    if (bn) "আগামীকালের খসড়া: $list।" else "Tomorrow's draft: $list."
                }
            )
        }

        // ---- Call (CONFIRMATION FIRST) ----
        extractCallTarget(original, lower)?.let { targetName ->
            val number = CallSmsHelper.findNumberByName(context, targetName) ?: targetName
            pendingAction = {
                val ok = try { CallSmsHelper.call(context, number); true } catch (e: Exception) { false }
                if (ok) (if (bn) "$targetName-কে কল করছি..." else "Calling $targetName...")
                else (if (bn) "কল করা যাচ্ছে না। CALL_PHONE অনুমতি দরকার।" else "Could not place the call. CALL_PHONE permission is needed.")
            }
            return Outcome.AskConfirmation(if (bn) "$targetName-কে কল করব?" else "Shall I call $targetName?")
        }

        // ---- SMS (CONFIRMATION FIRST): "রহিমকে মেসেজ পাঠাও আমি আসছি" ----
        if (lower.containsAny("মেসেজ পাঠা", "এসএমএস", "send sms", "send message", "text ")) {
            val parts = original.split(Regex("মেসেজ পাঠাও|এসএমএস পাঠাও|send sms|send message"), limit = 2)
            val targetName = parts.getOrNull(0)?.trim()?.trimEnd('ক', 'ে')?.trim().orEmpty()
            val message = parts.getOrNull(1)?.trim().orEmpty()
            if (targetName.isNotBlank() && message.isNotBlank()) {
                val number = CallSmsHelper.findNumberByName(context, targetName) ?: targetName
                pendingAction = {
                    if (CallSmsHelper.sendSms(context, number, message)) {
                        if (bn) "$targetName-কে মেসেজ পাঠিয়ে দিলাম।" else "Message sent to $targetName."
                    } else {
                        if (bn) "মেসেজ পাঠানো যায়নি। SEND_SMS অনুমতি দরকার।" else "Could not send. SEND_SMS permission is needed."
                    }
                }
                return Outcome.AskConfirmation(if (bn) "$targetName-কে এই মেসেজটা পাঠাব? \"$message\"" else "Shall I send this message to $targetName? \"$message\"")
            }
            return Outcome.Reply(if (bn) "কাকে, কী মেসেজ পাঠাব? যেমন: রহিমকে মেসেজ পাঠাও, আমি আসছি।" else "Who should I message, and what should it say?")
        }

        // ---- Missed calls ----
        if (lower.containsAny("মিসড কল", "missed call")) {
            val missed = CallReceiver.latestMissedCall(context)
            return Outcome.Reply(
                if (missed != null) (if (bn) "সর্বশেষ মিসড কল: $missed।" else "Latest missed call: $missed.")
                else (if (bn) "কোনো মিসড কল পাইনি।" else "No missed calls found.")
            )
        }

        // ---- Open app ----
        extractAfter(original, listOf("খোলো", "খোল", "open "))?.let { appName ->
            val opened = CallSmsHelper.openApp(context, appName.trim())
            return Outcome.Reply(
                if (opened != null) (if (bn) "$appName খুলে দিলাম।" else "Opening $appName.")
                else (if (bn) "$appName অ্যাপটা খুঁজে পাইনি।" else "I could not find the $appName app.")
            )
        }
        if (lower.containsAny("খোলো", "open")) {
            for (app in CallSmsHelper.knownApps) {
                if (lower.contains(app.lowercase()) || original.contains(app)) {
                    val opened = CallSmsHelper.openApp(context, app)
                    if (opened != null) return Outcome.Reply(if (bn) "$app খুলে দিলাম।" else "Opening $app.")
                }
            }
        }

        // ---- Thank you / bye ----
        if (lower.containsAny("ধন্যবাদ", "thank")) {
            return Outcome.Reply(if (bn) "আপনাকেও ধন্যবাদ! আর কিছু লাগলে \"জার্ভিস\" বললেই হবে।" else "You're welcome! Say \"Jarvis\" anytime.")
        }

        return Outcome.NeedsAi
    }

    // ---------- helpers ----------

    private fun isYes(lower: String) =
        lower.containsAny("হ্যাঁ", "হুম", "হে", "yes", "yeah", "ok", "ঠিক আছে", "করো", "পাঠাও", "ধরো")

    private fun isNo(lower: String) =
        lower.containsAny("না", "বাতিল", "no", "cancel", "থাক", "লাগবে না")

    private fun parseTime(norm: String): Pair<Int, Int>? {
        val m = Regex("(\\d{1,2})(?::(\\d{2}))?").find(norm) ?: return null
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
        val pm = norm.containsAny("pm", "দুপুর", "বিকেল", "সন্ধ্যা", "রাত", "বিকাল")
        val am = norm.containsAny("am", "সকাল", "ভোর")
        if (pm && hour < 12) hour += 12
        if (am && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    private fun nextOccurrence(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    private fun formatTime(h: Int, m: Int): String {
        val hour12 = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
        val suffix = if (h < 12) "AM" else "PM"
        return String.format("%d:%02d %s", hour12, m, suffix)
    }

    private fun extractAfter(text: String, markers: List<String>): String? {
        for (marker in markers) {
            val idx = text.lowercase().indexOf(marker.lowercase())
            if (idx >= 0) {
                val before = text.substring(0, idx).trim()
                val after = text.substring(idx + marker.length).trim()
                // For "X খোলো" the app name is BEFORE the marker; for "open X" it is AFTER.
                return (if (marker.trim().endsWith("o") || marker.contains("খোল")) before.ifBlank { after } else after.ifBlank { before })
            }
        }
        return null
    }

    private fun extractCallTarget(original: String, lower: String): String? {
        if (!lower.containsAny("কল কর", "ফোন কর", "call", "ফোন দাও")) return null
        var t = original
            .replace(Regex("(?i)(কল করো|কল কর|ফোন করো|ফোন কর|ফোন দাও|call|কল)"), "")
            .trim().trimEnd('ক', 'ে')
        if (t.endsWith("কে")) t = t.dropLast(2).trim()
        return t.ifBlank { null }
    }

    companion object {
        fun toEnglishDigits(s: String): String {
            val bnDigits = "০১২৩৪৫৬৭৮৯"
            val sb = StringBuilder()
            for (ch in s) {
                val i = bnDigits.indexOf(ch)
                sb.append(if (i >= 0) ('0' + i) else ch)
            }
            return sb.toString()
        }
    }
}

private fun String.containsAny(vararg needles: String): Boolean =
    needles.any { this.contains(it) }
