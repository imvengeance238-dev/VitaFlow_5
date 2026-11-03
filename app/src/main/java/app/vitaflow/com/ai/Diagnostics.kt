package app.vitaflow.com.ai

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Facts about why the app stopped or misbehaved, so a problem can be reported without guessing. */
object Diagnostics {
    private fun crashFile(context: Context) = File(context.filesDir, "last_crash.txt")

    fun saveCrash(context: Context, thread: String, e: Throwable) {
        runCatching {
            crashFile(context).writeText(
                "${Date()}\nthread: $thread\n" + android.util.Log.getStackTraceString(e)
            )
        }
    }

    private fun reasonName(r: Int): String = when (r) {
        ApplicationExitInfo.REASON_CRASH -> "CRASH (Java)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE (C++ কোড)"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY (RAM শেষ)"
        ApplicationExitInfo.REASON_ANR -> "ANR (app সাড়া দেয়নি)"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED (system বন্ধ করেছে)"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        else -> "OTHER ($r)"
    }

    fun report(context: Context, installedModels: Set<String>): String {
        val sb = StringBuilder()
        val fmt = SimpleDateFormat("d/M HH:mm", Locale.US)
        val am = context.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        sb.append("Vita Flow ${runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()}\n")
        sb.append("ফোন: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}\n")
        sb.append("RAM: মোট ${mi.totalMem / 1048576} MB, ফাঁকা ${mi.availMem / 1048576} MB\n")
        sb.append("Model: ${installedModels.sorted().joinToString(", ").ifEmpty { "কিছু নেই" }}\n\n")
        sb.append("সাম্প্রতিক বন্ধ হওয়া:\n")
        val list = runCatching { am.getHistoricalProcessExitReasons(context.packageName, 0, 6) }.getOrDefault(emptyList())
        if (list.isEmpty()) sb.append("(কিছু নেই)\n")
        list.forEach {
            sb.append("• ${fmt.format(Date(it.timestamp))} ${reasonName(it.reason)} ${it.description.orEmpty()}\n")
        }
        crashFile(context).takeIf { it.exists() }?.let {
            sb.append("\nশেষ crash:\n").append(it.readText().take(2500))
        }
        return sb.toString()
    }

    fun clearCrash(context: Context) {
        crashFile(context).delete()
    }
}
