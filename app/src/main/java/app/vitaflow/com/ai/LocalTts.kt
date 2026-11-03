package app.vitaflow.com.ai

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * The assistant's mouth: a local neural voice, played with AudioTrack. No Android TextToSpeech.
 * Each piece of text is turned into sound first, then played completely (the next piece is prepared while the
 * current one plays). Nothing in here is allowed to crash the app: every problem comes back as a short message.
 */
class LocalTts(private val models: ModelManager) {
    private class Clip(val samples: FloatArray, val rate: Int)

    private val engines = HashMap<String, OfflineTts>()
    private val allowed = HashMap<String, Set<Char>>()
    private val phraseCache = HashMap<String, Clip>()
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var idleJob: Job? = null
    @Volatile private var cancelled = false
    @Volatile private var track: AudioTrack? = null

    fun isReady(lang: String): Boolean = models.packFiles("$lang-tts") != null

    private fun engine(lang: String): OfflineTts? {
        engines[lang]?.let { return it }
        val (model, tokens) = models.packFiles("$lang-tts") ?: return null
        val tts = OfflineTts(
            config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(model = model, tokens = tokens),
                    numThreads = 2,
                    provider = "cpu"
                )
            )
        )
        engines[lang] = tts
        allowed[lang] = readTokenChars(tokens)
        return tts
    }

    private fun readTokenChars(tokensPath: String): Set<Char> {
        val set = mutableSetOf(' ')
        File(tokensPath).forEachLine { line ->
            val tok = line.substringBeforeLast(' ', "")
            if (tok.length == 1) set += tok[0]
        }
        return set
    }

    private fun detectLang(s: String, fallback: String): String {
        var bn = 0
        var latin = 0
        for (c in s) {
            if (c in '\u0980'..'\u09FF') bn++ else if (c in 'a'..'z' || c in 'A'..'Z') latin++
        }
        return when {
            bn == 0 && latin == 0 -> fallback
            bn >= latin -> "bn"
            else -> "en"
        }
    }

    private fun clean(text: String, lang: String): String {
        val ok = allowed[lang] ?: return text
        val base = text.map { c ->
            if (c in '\u09E6'..'\u09EF') '0' + (c - '\u09E6') else c
        }.joinToString("")
        val normalized = if (lang == "en") base.lowercase(Locale.ROOT) else base
        return normalized.filter { it in ok }.replace(Regex("\\s+"), " ").trim()
    }

    /** Sentences, and long sentences cut at spaces so no single piece takes long to prepare. */
    private fun pieces(text: String): List<String> {
        val out = ArrayList<String>()
        for (sentence in text.split(Regex("(?<=[।.!?\\n])\\s*")).map { it.trim() }.filter { it.isNotEmpty() }) {
            var rest = sentence
            while (rest.length > 110) {
                val cut = rest.lastIndexOf(' ', 110).takeIf { it > 40 } ?: 110
                out += rest.substring(0, cut).trim()
                rest = rest.substring(cut).trim()
            }
            if (rest.isNotEmpty()) out += rest
        }
        return out
    }

    private fun synth(tts: OfflineTts, lang: String, cleaned: String, speed: Float, cache: Boolean): Clip? {
        val key = "$lang|$cleaned"
        if (cache) phraseCache[key]?.let { return it }
        val audio = tts.generate(text = cleaned, sid = 0, speed = speed)
        if (audio.samples.isEmpty()) return null
        val clip = Clip(audio.samples, audio.sampleRate)
        if (cache) phraseCache[key] = clip
        return clip
    }

    /**
     * Speaks [text], switching between the Bengali and English voice sentence by sentence.
     * Returns null when all went well, otherwise a short reason why nothing (or not everything) could be heard.
     */
    suspend fun speak(text: String, speed: Float = 1.0f, cache: Boolean = false): String? = mutex.withLock {
        idleJob?.cancel()
        val problem = withContext(Dispatchers.IO) {
            var problem: String? = null
            cancelled = false
            try {
                // decide the voice and the cleaned text of every piece
                val todo = ArrayList<Triple<OfflineTts, String, String>>()
                for (piece in pieces(text)) {
                    val lang = detectLang(piece, "bn")
                    val label = if (lang == "bn") "বাংলা" else "English"
                    val tts = try {
                        engine(lang)
                    } catch (t: Throwable) {
                        problem = "$label voice চালু করা যায়নি: ${t.message ?: t.javaClass.simpleName}"
                        null
                    }
                    if (tts == null) {
                        if (problem == null) problem = "$label voice model যোগ করা নেই (প্রোফাইল > সেটিংস > Model)"
                        continue
                    }
                    val cleaned = clean(piece, lang)
                    if (cleaned.isNotBlank()) todo += Triple(tts, lang, cleaned)
                }
                if (todo.isEmpty() && problem == null) problem = "বলার মতো কোনো লেখা পাওয়া যায়নি"

                coroutineScope {
                    fun prepare(i: Int): Deferred<Clip?> = async(Dispatchers.Default) {
                        val (tts, lang, cleaned) = todo[i]
                        synth(tts, lang, cleaned, speed, cache)
                    }
                    var next: Deferred<Clip?> = if (todo.isNotEmpty()) prepare(0) else CompletableDeferred(null)
                    for (i in todo.indices) {
                        val clip = next.await()
                        next = if (i + 1 < todo.size) prepare(i + 1) else CompletableDeferred(null)
                        if (cancelled) break
                        if (clip == null) {
                            problem = "শব্দ তৈরি হয়নি (model বা লেখা ঠিক নেই)"
                            continue
                        }
                        play(clip)
                    }
                    next.cancel()
                }
            } catch (t: Throwable) {
                problem = "voice বাজানো যায়নি: ${t.message ?: t.javaClass.simpleName}"
            }
            problem
        }
        scheduleRelease()
        problem
    }

    /** Plays one clip completely, and only then lets go of the speaker. */
    private fun play(clip: Clip) {
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(clip.rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(clip.samples.size * 4)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track = t
        try {
            val written = t.write(clip.samples, 0, clip.samples.size, AudioTrack.WRITE_BLOCKING)
            if (written <= 0) error("audio লেখা যায়নি (কোড $written)")
            t.setVolume(1.0f)
            t.play()
            val total = clip.samples.size
            val deadline = System.currentTimeMillis() + total * 1000L / clip.rate + 1500
            while (!cancelled && t.playbackHeadPosition < total && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }
        } finally {
            runCatching { t.stop() }
            runCatching { t.release() }
            track = null
        }
    }

    /** Stops the voice right away (the user pressed stop or sent a new command). */
    fun stop() {
        cancelled = true
        track?.let { runCatching { it.pause() } }
    }

    /** A voice model takes memory, so it is dropped a little after the last sentence. */
    private fun scheduleRelease() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(25_000)
            mutex.withLock {
                engines.values.forEach { runCatching { it.release() } }
                engines.clear()
                allowed.clear()
            }
        }
    }

    /** For the "voice test" in settings: says what works and what does not, then plays a short sentence. */
    suspend fun diagnose(lang: String): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            val sb = StringBuilder()
            if (models.packFiles("$lang-tts") == null) return@withContext "voice model যোগ করা নেই।"
            try {
                val t0 = System.currentTimeMillis()
                val tts = engine(lang) ?: return@withContext "model লোড হয়নি।"
                sb.append("লোড হয়েছে (${System.currentTimeMillis() - t0} ms), sample rate ${tts.sampleRate()}\n")
                val cleaned = clean(if (lang == "bn") "আমি ম্যাক্স, আপনার সহকারী।" else "Hello, I am Max.", lang)
                sb.append("পাঠ্য: $cleaned\n")
                val t1 = System.currentTimeMillis()
                val clip = synth(tts, lang, cleaned, 1.0f, false)
                if (clip == null) {
                    sb.append("কোনো শব্দ তৈরি হয়নি, মানে model বা পাঠ্য ঠিক নেই।")
                } else {
                    sb.append("শব্দ তৈরি: ${clip.samples.size} নমুনা (${System.currentTimeMillis() - t1} ms)\n")
                    cancelled = false
                    play(clip)
                    sb.append("বাজানো শেষ। শুনতে না পেলে ফোনের media volume দেখুন।")
                }
            } catch (t: Throwable) {
                sb.append("ত্রুটি: ${t.javaClass.simpleName}: ${t.message}")
            }
            scheduleRelease()
            sb.toString()
        }
    }

    suspend fun release() = mutex.withLock {
        engines.values.forEach { runCatching { it.release() } }
        engines.clear()
        allowed.clear()
    }
}
