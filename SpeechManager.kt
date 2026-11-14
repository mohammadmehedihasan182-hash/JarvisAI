package com.doodlejoy.jarvis.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.doodlejoy.jarvis.core.Prefs
import java.util.Locale

/**
 * One engine for BOTH directions of voice:
 *  - listen(): SpeechRecognizer in the user's chosen language (বাংলা bn-BD / English en-US)
 *  - speak():  TextToSpeech in the same language; JARVIS always answers out loud.
 */
class SpeechManager(private val context: Context) : TextToSpeech.OnInitListener {

    private val prefs = Prefs(context)
    private val main = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null

    var onListeningState: ((Boolean) -> Unit)? = null
    var onSpeakDone: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            applyVoiceSettings()
            ttsReady = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    main.post { onSpeakDone?.invoke() }
                }
                @Deprecated("deprecated")
                override fun onError(utteranceId: String?) {
                    main.post { onSpeakDone?.invoke() }
                }
            })
        }
    }

    fun locale(): Locale =
        if (prefs.language == "en") Locale.US else Locale("bn", "BD")

    private fun applyVoiceSettings() {
        tts?.language = locale()
        tts?.setSpeechRate(0.7f + prefs.voiceRate / 100f * 0.6f) // settings slider
    }

    /** Speak text out loud (and refresh voice settings first, cheap). */
    fun speak(text: String, flush: Boolean = true) {
        if (!ttsReady) return
        applyVoiceSettings()
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(text, mode, null, "jarvis_utterance")
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    val isSpeaking: Boolean get() = tts?.isSpeaking == true

    /**
     * Listen once. onResult gets the best transcript, onError a short message.
     * Used by: mic button, conversation mode, wake-word service.
     */
    fun listen(onResult: (String) -> Unit, onError: (String) -> Unit = {}) {
        main.post {
            destroyRecognizer()
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onError("এই ফোনে ভয়েস রিকগনিশন পাওয়া যাচ্ছে না")
                return@post
            }
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { onListeningState?.invoke(true) }
                    override fun onEndOfSpeech() { onListeningState?.invoke(false) }
                    override fun onError(error: Int) {
                        onListeningState?.invoke(false)
                        onError(
                            when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH -> "শুনতে পাইনি, আবার বলুন"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "সময় শেষ"
                                SpeechRecognizer.ERROR_NETWORK -> "নেটওয়ার্ক সমস্যা"
                                else -> "ভয়েস এরর: $error"
                            }
                        )
                    }
                    override fun onResults(results: Bundle?) {
                        onListeningState?.invoke(false)
                        val text = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                        if (text.isNullOrBlank()) onError("শুনতে পাইনি, আবার বলুন") else onResult(text)
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale().toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                startListening(intent)
            }
        }
    }

    fun stopListening() {
        main.post {
            recognizer?.stopListening()
            onListeningState?.invoke(false)
        }
    }

    private fun destroyRecognizer() {
        recognizer?.destroy()
        recognizer = null
    }

    fun shutdown() {
        tts?.shutdown()
        destroyRecognizer()
    }
}
