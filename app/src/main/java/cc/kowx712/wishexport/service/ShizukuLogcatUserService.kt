package cc.kowx712.wishexport.service

import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.Keep
import java.io.FileOutputStream

/**
 * Shizuku user service implementation.
 * This runs with Shizuku's elevated permissions.
 */
@Keep
class ShizukuLogcatUserService : IShizukuLogcatService.Stub() {

    companion object { private const val TAG = "ShizukuLogcatUserService" }

    private var logcatProcess: Process? = null
    private var logcatOutput: ParcelFileDescriptor? = null
    private var executeFailureLogged = false

    override fun executeLogcat(command: Array<String>): String {
        return try {
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            executeFailureLogged = false
            output
        } catch (e: Exception) {
            if (!executeFailureLogged) {
                Log.e(TAG, "executeLogcat failed", e)
                executeFailureLogged = true
            }
            "Error: ${e.message}"
        }
    }

    override fun clearLogcat() {
        try {
            val process = ProcessBuilder("/system/bin/logcat", "-c")
                .redirectErrorStream(true)
                .start()
            if (process.waitFor() != 0) {
                Log.w(TAG, "Failed to clear logcat")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear logcat", e)
        }
    }

    @Synchronized
    override fun startLogcat(command: Array<String>): ParcelFileDescriptor {
        stopLogcat()
        val pipe = ParcelFileDescriptor.createPipe()
        val process = Runtime.getRuntime().exec(command)
        logcatProcess = process
        logcatOutput = pipe[1]

        Thread {
            try {
                process.inputStream.use { input ->
                    FileOutputStream(pipe[1].fileDescriptor).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (_: Exception) {
                // The pipe is closed when capture is stopped or the process exits.
            } finally {
                try { process.destroy() } catch (_: Exception) { }
                synchronized(this) {
                    if (logcatProcess === process) {
                        logcatProcess = null
                        logcatOutput = null
                    }
                }
            }
        }.apply { isDaemon = true }.start()

        return pipe[0]
    }

    @Synchronized
    override fun stopLogcat() {
        try { logcatProcess?.destroy() } catch (_: Exception) { }
        try { logcatOutput?.close() } catch (_: Exception) { }
        logcatProcess = null
        logcatOutput = null
    }
}
