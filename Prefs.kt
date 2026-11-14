package com.doodlejoy.jarvis.core

import android.content.Context

/**
 * Memory (spec section 6): JARVIS remembers the user's preferences —
 * name, language and voice. Stored on-device in SharedPreferences.
 * Sensitive actions are NEVER auto-confirmed here; see AssistantBrain.
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)

    var userName: String
        get() = sp.getString(KEY_NAME, "") ?: ""
        set(value) = sp.edit().putString(KEY_NAME, value).apply()

    /** "bn" (বাংলা) or "en" (English) */
    var language: String
        get() = sp.getString(KEY_LANG, "bn") ?: "bn"
        set(value) = sp.edit().putString(KEY_LANG, value).apply()

    /** Voice speed slider, 0..100 */
    var voiceRate: Int
        get() = sp.getInt(KEY_RATE, 50)
        set(value) = sp.edit().putInt(KEY_RATE, value).apply()

    var wakeWordEnabled: Boolean
        get() = sp.getBoolean(KEY_WAKE, false)
        set(value) = sp.edit().putBoolean(KEY_WAKE, value).apply()

    companion object {
        private const val KEY_NAME = "user_name"
        private const val KEY_LANG = "language"
        private const val KEY_RATE = "voice_rate"
        private const val KEY_WAKE = "wake_word"
    }
}
