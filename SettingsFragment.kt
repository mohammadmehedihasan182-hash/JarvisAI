package com.doodlejoy.jarvis.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.doodlejoy.jarvis.JarvisApp
import com.doodlejoy.jarvis.core.Prefs
import com.doodlejoy.jarvis.databinding.FragmentSettingsBinding

/**
 * Settings tab (spec section 6 — memory & safety):
 * the user's name, language and voice speed are REMEMBERED here and
 * used everywhere (greetings, speech recognition, TTS).
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: Prefs

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())

        binding.userName.setText(prefs.userName)
        if (prefs.language == "en") binding.langEn.isChecked = true else binding.langBn.isChecked = true
        binding.voiceRate.progress = prefs.voiceRate

        binding.saveSettings.setOnClickListener {
            prefs.userName = binding.userName.text?.toString()?.trim().orEmpty()
            prefs.language = if (binding.langEn.isChecked) "en" else "bn"
            prefs.voiceRate = binding.voiceRate.progress
            Toast.makeText(requireContext(), "সেভ হয়েছে", Toast.LENGTH_SHORT).show()
            JarvisApp.instance.speech.speak(
                if (prefs.language == "en") "Saved. I will remember your preferences."
                else "সেভ করে রাখলাম। আপনার পছন্দ মনে থাকবে।"
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
