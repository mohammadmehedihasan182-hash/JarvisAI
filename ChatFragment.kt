package com.doodlejoy.jarvis.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.doodlejoy.jarvis.JarvisApp
import com.doodlejoy.jarvis.ai.AiChatClient
import com.doodlejoy.jarvis.core.AssistantBrain
import com.doodlejoy.jarvis.core.Prefs
import com.doodlejoy.jarvis.databinding.FragmentChatBinding
import com.doodlejoy.jarvis.databinding.ItemMessageBinding
import kotlinx.coroutines.launch

data class ChatMessage(val text: String, val fromUser: Boolean)

/**
 * Chat tab: same brain as voice, in writing.
 * Every JARVIS reply is also spoken out loud (spec: এআই উত্তর বলে শোনাবে).
 */
class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: MessageAdapter
    private lateinit var brain: AssistantBrain
    private lateinit var prefs: Prefs

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        brain = AssistantBrain(requireContext())
        prefs = Prefs(requireContext())
        adapter = MessageAdapter(messages)
        binding.chatList.layoutManager = LinearLayoutManager(requireContext())
        binding.chatList.adapter = adapter

        addMessage(ChatMessage(if (prefs.language == "en") "Hello! I am JARVIS. Type or use the mic." else "আসসালামু আলাইকুম! আমি জার্ভিস। লিখুন অথবা মাইকে বলুন।", fromUser = false), speak = false)

        binding.chatSend.setOnClickListener { sendFromInput() }
        binding.chatMic.setOnClickListener {
            JarvisApp.instance.speech.listen(onResult = { heard ->
                binding.chatInput.setText(heard)
                sendFromInput()
            })
        }
    }

    private fun sendFromInput() {
        val text = binding.chatInput.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        binding.chatInput.text?.clear()
        addMessage(ChatMessage(text, fromUser = true), speak = false)
        respond(text)
    }

    private fun respond(input: String) {
        when (val outcome = brain.handle(input)) {
            is AssistantBrain.Outcome.Reply -> addMessage(ChatMessage(outcome.text, false))
            is AssistantBrain.Outcome.AskConfirmation -> addMessage(ChatMessage(outcome.question, false))
            AssistantBrain.Outcome.NeedsAi -> viewLifecycleOwner.lifecycleScope.launch {
                val reply = AiChatClient.ask(input, prefs.language)
                addMessage(
                    ChatMessage(
                        reply ?: if (prefs.language == "en") "I don't know that yet. Try a command like: today's schedule."
                        else "এটা এখনো শিখিনি। কমান্ড বলুন, যেমন: আজকের কাজ।",
                        false
                    )
                )
            }
        }
    }

    private fun addMessage(msg: ChatMessage, speak: Boolean = true) {
        messages.add(msg)
        adapter.notifyItemInserted(messages.size - 1)
        binding.chatList.scrollToPosition(messages.size - 1)
        if (!msg.fromUser && speak) JarvisApp.instance.speech.speak(msg.text)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class MessageAdapter(private val items: List<ChatMessage>) :
    RecyclerView.Adapter<MessageAdapter.VH>() {

    class VH(val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val msg = items[position]
        holder.binding.bubbleText.text = msg.text
        val root = holder.binding.bubbleRoot
        root.gravity = if (msg.fromUser) Gravity.END else Gravity.START
        holder.binding.bubbleText.setBackgroundColor(
            if (msg.fromUser) Color.parseColor("#00586B") else Color.parseColor("#12233D")
        )
        (holder.binding.bubbleText.layoutParams as? LinearLayout.LayoutParams)?.gravity =
            if (msg.fromUser) Gravity.END else Gravity.START
    }

    override fun getItemCount() = items.size
}
