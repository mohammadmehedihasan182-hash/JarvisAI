package com.doodlejoy.jarvis.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.doodlejoy.jarvis.JarvisApp
import com.doodlejoy.jarvis.ai.AiChatClient
import com.doodlejoy.jarvis.core.AssistantBrain
import com.doodlejoy.jarvis.core.PhoneInfo
import com.doodlejoy.jarvis.core.Prefs
import com.doodlejoy.jarvis.databinding.FragmentHomeBinding
import com.doodlejoy.jarvis.voice.WakeWordService
import kotlinx.coroutines.launch

/**
 * Home (spec sections 1–3):
 * glowing ring + avatar face + speech bubble + big mic button,
 * conversation mode, wake-word toggle, live phone info.
 *
 * Conversation mode (spec section 2): after JARVIS finishes speaking it
 * listens again by itself; if the user stays silent ~2 rounds, it sleeps.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val app get() = JarvisApp.instance
    private val speech get() = app.speech
    private lateinit var brain: AssistantBrain
    private lateinit var prefs: Prefs

    private var conversationMode = false
    private var silenceCount = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        brain = AssistantBrain(requireContext())
        prefs = Prefs(requireContext())

        say(
            if (prefs.language == "en") "Hello! I am JARVIS. Tap the mic and speak."
            else "আসসালামু আলাইকুম! আমি জার্ভিস। মাইকে চাপ দিয়ে কথা বলুন।",
            speakAloud = false
        )
        refreshPhoneInfo()

        speech.onListeningState = { listening ->
            if (_binding == null) return@onListeningState
            binding.ringView.state = if (listening) GlowingRingView.State.LISTENING else GlowingRingView.State.IDLE
            binding.statusText.text = if (listening) "শুনছি..." else "বলতে মাইকে চাপ দিন"
        }
        speech.onSpeakDone = {
            if (_binding == null) return@onSpeakDone
            binding.ringView.state = GlowingRingView.State.IDLE
            if (conversationMode) listenOnce()
        }

        binding.micButton.setOnClickListener {
            silenceCount = 0
            listenOnce()
        }

        binding.conversationToggle.setOnClickListener {
            if (conversationMode) stopConversation() else startConversationMode()
        }

        updateWakeButton()
        binding.wakeWordToggle.setOnClickListener {
            if (prefs.wakeWordEnabled) {
                requireContext().stopService(Intent(requireContext(), WakeWordService::class.java))
                prefs.wakeWordEnabled = false
            } else {
                // Android 14 asks users to allow the microphone foreground service from settings
                // on some phones; if start fails we still try the regular start.
                ContextCompat.startForegroundService(requireContext(), Intent(requireContext(), WakeWordService::class.java))
                prefs.wakeWordEnabled = true
                Toast.makeText(
                    requireContext(),
                    "ওয়েক ওয়ার্ড চালু। নোট: ব্যাটারি সেটিংসে এই অ্যাপকে Unrestricted করে দিন।",
                    Toast.LENGTH_LONG
                ).show()
                openBatterySettingsHint()
            }
            updateWakeButton()
        }
    }

    private fun updateWakeButton() {
        if (_binding == null) return
        binding.wakeWordToggle.text =
            if (prefs.wakeWordEnabled) "ওয়েক ওয়ার্ড বন্ধ করুন" else "ওয়েক ওয়ার্ড (\"জার্ভিস\") চালু করুন"
        binding.ringView.state =
            if (prefs.wakeWordEnabled) GlowingRingView.State.IDLE else GlowingRingView.State.SLEEPING
    }

    private fun openBatterySettingsHint() {
        // Best effort: open this app's details so the user can set Battery = Unrestricted.
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${requireContext().packageName}")
            }
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    /** One listening round: hear -> think -> speak. */
    private fun listenOnce() {
        speech.listen(
            onResult = { heard ->
                silenceCount = 0
                binding.statusText.text = "আপনি: $heard"
                handleCommand(heard)
            },
            onError = { _ ->
                if (conversationMode) {
                    silenceCount++
                    if (silenceCount >= 2) {
                        say(if (prefs.language == "en") "Going to sleep. Say \"Jarvis\" to wake me." else "আমি ঘুমিয়ে গেলাম। জাগাতে \"জার্ভিস\" বলুন।")
                        stopConversation()
                    } else {
                        handler.postDelayed({ if (conversationMode) listenOnce() }, 600)
                    }
                }
            }
        )
    }

    fun handleCommand(input: String) {
        when (val outcome = brain.handle(input)) {
            is AssistantBrain.Outcome.Reply -> say(outcome.text)
            is AssistantBrain.Outcome.AskConfirmation -> say(outcome.question)
            AssistantBrain.Outcome.NeedsAi -> {
                // Free chat goes to the user's server (key stays on the server).
                viewLifecycleOwner.lifecycleScope.launch {
                    val reply = AiChatClient.ask(input, prefs.language)
                    say(
                        reply ?: if (prefs.language == "en") {
                            "I don't know that command yet. Try: set an alarm, call someone, today's schedule, battery."
                        } else {
                            "এই কমান্ডটা এখনো শিখিনি। বলুন: অ্যালার্ম দাও, কল করো, আজকের কাজ, ব্যাটারি কত।"
                        }
                    )
                }
            }
        }
    }

    /** Show in the speech bubble and (optionally) speak out loud. */
    fun say(text: String, speakAloud: Boolean = true) {
        if (_binding == null) return
        binding.speechBubble.text = text
        if (speakAloud) {
            binding.ringView.state = GlowingRingView.State.SPEAKING
            speech.speak(text)
            // If TTS is unavailable, onSpeakDone never fires — keep conversation mode moving.
            if (speech.isSpeaking.not()) {
                handler.postDelayed({ speech.onSpeakDone?.invoke() }, 1200)
            }
        }
    }

    fun startConversationMode() {
        conversationMode = true
        silenceCount = 0
        if (_binding != null) {
            binding.conversationToggle.text = "কথোপকথন মোড বন্ধ করুন"
            say(if (prefs.language == "en") "Conversation mode on. I am listening." else "কথোপকথন মোড চালু। বলুন, শুনছি।")
            // onSpeakDone will trigger listenOnce()
        }
    }

    private fun stopConversation() {
        conversationMode = false
        speech.stopListening()
        if (_binding != null) binding.conversationToggle.text = "কথোপকথন মোড চালু করুন"
    }

    private fun refreshPhoneInfo() {
        binding.phoneInfo.text = "📱 " + PhoneInfo.summary(requireContext(), prefs.language != "en")
        handler.postDelayed({ if (_binding != null) refreshPhoneInfo() }, 30_000)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
