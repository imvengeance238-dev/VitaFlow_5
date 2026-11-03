package app.vitaflow.com.ai

import android.os.Process
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * Heavy model work runs on threads with a low priority. Threads created by the model code inherit it,
 * so the screen keeps getting its share of the processor and does not freeze while Max is thinking.
 */
object Workers {
    private fun lowPriority(name: String) = Executors.defaultThreadFactory().let { factory ->
        java.util.concurrent.ThreadFactory { r ->
            Thread({
                // 5 = a little less important than the screen, but still in the normal CPU group.
                // (The real "background" priority would push the work onto the slow cores.)
                Process.setThreadPriority(5)
                r.run()
            }, name)
        }
    }

    /** One thread for the language model. */
    val llm: ExecutorCoroutineDispatcher = Executors.newSingleThreadExecutor(lowPriority("vita-llm")).asCoroutineDispatcher()

    /** Speech recognition and similar. */
    val background: ExecutorCoroutineDispatcher = Executors.newFixedThreadPool(2, lowPriority("vita-bg")).asCoroutineDispatcher()
}
