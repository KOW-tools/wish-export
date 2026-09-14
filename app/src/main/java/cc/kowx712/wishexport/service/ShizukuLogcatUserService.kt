package cc.kowx712.wishexport.service

import android.util.Log
import androidx.annotation.Keep
import cc.kowx712.wishexport.model.GameConfig
import java.io.BufferedReader
import java.io.InputStreamReader

/** Runs logcat and URL matching under Shizuku's shell/root identity. */
@Keep
class ShizukuLogcatUserService : IShizukuLogcatService.Stub() {
    companion object { private const val TAG = "ShizukuLogcatUserService" }

    private var logcatProcess: Process? = null
    private var captureThread: Thread? = null

    @Synchronized
    override fun startCapture(callback: IShizukuLogcatCallback) {
        stopCapture()
        clearLogcat()
        val process = try {
            ProcessBuilder(
                "/system/bin/logcat", "-b", "main", "-b", "system", "-v", "raw", "*:V"
            ).redirectErrorStream(true).start()
        } catch (e: Exception) {
            callback.onCaptureError(e.message ?: "Failed to start logcat")
            return
        }
        logcatProcess = process
        captureThread = Thread {
            try {
                BufferedReader(InputStreamReader(process.inputStream), 32768).use { reader ->
                    while (!Thread.currentThread().isInterrupted) {
                        val line = reader.readLine() ?: break
                        val url = GameConfig.SUPPORTED_CONFIGS.firstNotNullOfOrNull { config ->
                            config.urlPattern.find(line)?.value
                        }
                        if (url != null) {
                            if (isCurrent(process)) callback.onUrlFound(url)
                            return@Thread
                        }
                    }
                }
                if (isCurrent(process)) callback.onCaptureFinished()
            } catch (e: Exception) {
                if (isCurrent(process)) {
                    Log.e(TAG, "Logcat capture failed", e)
                    callback.onCaptureError(e.message ?: "Logcat capture failed")
                }
            } finally {
                process.destroy()
                synchronized(this) {
                    if (logcatProcess === process) {
                        logcatProcess = null
                        captureThread = null
                    }
                }
            }
        }.apply { name = "wish-logcat-capture"; isDaemon = true; start() }
    }

    @Synchronized
    override fun stopCapture() {
        captureThread?.interrupt()
        logcatProcess?.destroy()
        logcatProcess = null
        captureThread = null
    }

    private fun clearLogcat() {
        try {
            ProcessBuilder("/system/bin/logcat", "-c").start().waitFor()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear logcat", e)
        }
    }

    @Synchronized
    private fun isCurrent(process: Process): Boolean = logcatProcess === process
}
