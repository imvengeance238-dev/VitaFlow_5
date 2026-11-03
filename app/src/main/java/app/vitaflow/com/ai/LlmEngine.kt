package app.vitaflow.com.ai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/** Remote Max brain. API credentials are read from SecretStore and never written to source code. */
class LlmEngine(
    private val secrets: SecretStore
) {
    private val mutex = Mutex()
    private val sessions = linkedMapOf<String, MutableList<JSONObject>>()
    // Gemini API-র OpenAI-সামঞ্জস্যপূর্ণ এন্ডপয়েন্ট। মডেলের নাম Settings → Max AI থেকে বদলানো যায়।
    private val endpoint = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"

    // Kept synchronous for the existing Max pipeline. A missing key is reported by the request itself.
    fun isReady(): Boolean = true
    fun isLoaded(): Boolean = true
    suspend fun warmUp() = Unit
    fun keepSeconds(): Int = 0
    fun setKeepSeconds(n: Int) = Unit
    fun gpuEnabled(): Boolean = false
    suspend fun setGpu(on: Boolean) = Unit
    suspend fun release() { mutex.withLock { sessions.clear() } }

    private suspend fun apiKey(): String = secrets.geminiKey()?.takeIf { it.isNotBlank() }
        ?: error("API Key নেই। Settings → Max AI-তে Gemini API Key সংরক্ষণ করুন।")

    private fun requestBody(messages: JSONArray, temperature: Double, stream: Boolean): JSONObject =
        JSONObject().put("messages", messages).put("temperature", temperature.coerceIn(0.0, 1.0)).put("stream", stream)

    private suspend fun post(body: JSONObject, onText: ((String) -> Unit)? = null): String = withContext(Dispatchers.IO) {
        val key = apiKey()
        body.put("model", secrets.geminiModelName())
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", if (onText == null) "application/json" else "text/event-stream")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                // Gemini কখনো {"error":{...}}, কখনো [{"error":{...}}] পাঠায়—দুটোই সামলানো হচ্ছে
                val detail = runCatching {
                    val trimmed = err.trim()
                    val obj = if (trimmed.startsWith("[")) JSONArray(trimmed).getJSONObject(0) else JSONObject(trimmed)
                    obj.optJSONObject("error")?.optString("message")
                }.getOrNull()
                error(detail?.takeIf { it.isNotBlank() } ?: "AI সার্ভার অনুরোধ গ্রহণ করেনি (HTTP $code)")
            }
            if (onText == null) {
                val raw = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(raw).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                    ?.takeIf { it.isNotBlank() } ?: error("AI থেকে খালি উত্তর এসেছে।")
            } else {
                val out = StringBuilder()
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).useLines { lines ->
                    lines.forEach { line ->
                        if (!line.startsWith("data:")) return@forEach
                        val data = line.removePrefix("data:").trim()
                        if (data == "[DONE]") return@forEach
                        val part = runCatching {
                            JSONObject(data).optJSONArray("choices")?.optJSONObject(0)
                                ?.optJSONObject("delta")?.optString("content").orEmpty()
                        }.getOrDefault("")
                        if (part.isNotEmpty()) { out.append(part); onText(out.toString()) }
                    }
                }
                out.toString().ifBlank { error("AI থেকে খালি উত্তর এসেছে।") }
            }
        } finally { conn.disconnect() }
    }

    suspend fun complete(system: String, user: String, temperature: Double = 0.2): String {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", user))
        return post(requestBody(messages, temperature, false))
    }

    suspend fun completeSession(key: String, system: String, user: String, temperature: Double = 0.1, maxTurns: Int = 6): String = mutex.withLock {
        val history = sessions.getOrPut(key) { mutableListOf(JSONObject().put("role", "system").put("content", system)) }
        if (history.isEmpty()) history.add(JSONObject().put("role", "system").put("content", system))
        else history[0] = JSONObject().put("role", "system").put("content", system)
        if (history.size > maxTurns * 2) { val sys = history.first(); while (history.size > maxTurns * 2) history.removeAt(1); history[0] = sys }
        history.add(JSONObject().put("role", "user").put("content", user))
        val answer = runCatching { post(requestBody(JSONArray(history), temperature, false)) }
            .getOrElse { history.removeAt(history.lastIndex); throw it }
        history.add(JSONObject().put("role", "assistant").put("content", answer))
        answer
    }

    suspend fun testConnection(): String = complete("Reply with a short greeting.", "Connection test", 0.2)

    suspend fun stream(system: String, user: String, temperature: Double = 0.7, onText: (String) -> Unit): String {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", user))
        return post(requestBody(messages, temperature, true), onText)
    }

    suspend fun askMultimodal(system: String, text: String, imagePaths: List<String>, temperature: Double = 0.3, onText: (String) -> Unit): String {
        val parts = JSONArray().put(JSONObject().put("type", "text").put("text", text))
        imagePaths.take(4).forEach { path ->
            val file = File(path)
            require(file.isFile && file.length() <= 8L * 1024 * 1024) { "ছবিটি পাওয়া যায়নি অথবা ৮ MB-এর বেশি।" }
            val mime = when (file.extension.lowercase()) { "png" -> "image/png"; "webp" -> "image/webp"; else -> "image/jpeg" }
            val encoded = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
            parts.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:$mime;base64,$encoded")))
        }
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", parts))
        return post(requestBody(messages, temperature, true), onText)
    }
}
