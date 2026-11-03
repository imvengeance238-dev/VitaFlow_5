package app.vitaflow.com.ai

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The assistant's ears: turns speech into text on the phone, no Android SpeechRecognizer involved.
 * Only one language is kept in memory at a time, and no failure is allowed to crash the app.
 */
class LocalAsr(private val models: ModelManager) {
    private val recognizers = HashMap<String, OfflineRecognizer>()
    private val mutex = Mutex()

    fun isReady(lang: String): Boolean = models.packFiles("$lang-stt") != null

    /** [lang] is "bn" or "en". Throws a readable error if the model has not been added yet. */
    suspend fun transcribe(samples: FloatArray, lang: String): String = mutex.withLock {
        withContext(Workers.background) {
            try {
                // keep memory small: drop the other language first
                recognizers.entries.removeAll { entry ->
                    if (entry.key != lang) {
                        runCatching { entry.value.release() }
                        true
                    } else false
                }
                val recognizer = recognizers.getOrPut(lang) {
                    val (model, tokens) = models.packFiles("$lang-stt")
                        ?: error("${if (lang == "bn") "বাংলা" else "English"} শোনার model যোগ করা নেই")
                    OfflineRecognizer(
                        config = OfflineRecognizerConfig(
                            featConfig = FeatureConfig(sampleRate = VoiceRecorder.SAMPLE_RATE, featureDim = 80),
                            modelConfig = OfflineModelConfig(
                                nemo = OfflineNemoEncDecCtcModelConfig(model = model),
                                tokens = tokens,
                                numThreads = 2,
                                provider = "cpu"
                            )
                        )
                    )
                }
                val stream = recognizer.createStream()
                try {
                    stream.acceptWaveform(samples, VoiceRecorder.SAMPLE_RATE)
                    recognizer.decode(stream)
                    recognizer.getResult(stream).text.trim()
                } finally {
                    stream.release()
                }
            } catch (e: IllegalStateException) {
                throw e
            } catch (t: Throwable) {
                throw IllegalStateException("শোনার model চালু হয়নি: ${t.message ?: t.javaClass.simpleName}")
            }
        }
    }

    /** For callers that are not coroutines (it is only used just before the big model loads). */
    fun releaseNow() {
        runBlocking { release() }
    }

    suspend fun release() = mutex.withLock {
        recognizers.values.forEach { runCatching { it.release() } }
        recognizers.clear()
    }
}
