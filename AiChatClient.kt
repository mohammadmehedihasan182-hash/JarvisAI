package com.doodlejoy.jarvis.ai

import com.doodlejoy.jarvis.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Free-form AI chat (spec sections 2 & 6).
 *
 * SECURITY BY DESIGN: the API key is NOT in the app and NOT on the phone.
 * The app only talks to YOUR server (BuildConfig.AI_SERVER_URL), and the
 * server holds the key and calls the AI provider. Example server contract:
 *
 *   POST /chat   { "message": "...", "language": "bn" }
 *   -> 200       { "reply": "..." }
 *
 * If the server is not set up yet, ask() returns null and the built-in
 * AssistantBrain answers with its local rules instead.
 */
object AiChatClient {

    suspend fun ask(message: String, language: String): String? = withContext(Dispatchers.IO) {
        try {
            if (BuildConfig.AI_SERVER_URL.contains("your-server")) return@withContext null
            val conn = URL(BuildConfig.AI_SERVER_URL).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.doOutput = true
            val body = JSONObject().put("message", message).put("language", language).toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode != 200) return@withContext null
            val text = conn.inputStream.bufferedReader().readText()
            JSONObject(text).optString("reply").ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }
}
