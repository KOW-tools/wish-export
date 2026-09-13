package cc.kowx712.wishexport.service

import android.content.ComponentName
import android.os.ParcelFileDescriptor
import android.util.Log
import cc.kowx712.wishexport.model.AccessMode
import cc.kowx712.wishexport.model.GameConfig
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream

private const val LOGCAT_CAPTURE_SERVICE = "LogcatCaptureService"
private const val SHIZUKU_LOGCAT_TAG = "ShizukuLogcatService"
private const val ROOT_LOGCAT_TAG = "RootLogcatService"

/**
 * Abstract base class for logcat capture services.
 * Implements the Template Method pattern for extensibility.
 */
abstract class LogcatCaptureService {

    companion object {
        private const val TAG = LOGCAT_CAPTURE_SERVICE
        private const val TIMEOUT_MS = 600000L // 10 minutes timeout
    }

    /**
     * Starts capturing logcat and returns the first matching wish URL.
     * @return The captured wish URL, or null if no URL found within timeout
     */
    open suspend fun captureWishUrl(): String? = withContext(Dispatchers.IO) {
        try {
            clearLogcat()
            val process = startLogcatProcess()
            val url = readLogcatOutput(process)
            process.destroy()
            url
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing wish URL", e)
            null
        }
    }

    /**
     * Clears the logcat buffer.
     */
    protected abstract suspend fun clearLogcat()

    /**
     * Starts the logcat process with appropriate filters.
     * @return The Process object
     */
    protected abstract fun startLogcatProcess(): Process

    /**
     * Reads logcat output and extracts the first matching wish URL.
     */
    private fun readLogcatOutput(process: Process): String? {
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val startTime = System.currentTimeMillis()

        try {
            var line: String?
            while (System.currentTimeMillis() - startTime < TIMEOUT_MS) {
                line = reader.readLine()

                if (line == null) {
                    Thread.sleep(100)
                    continue
                }

                // Skip our own app's logs to prevent infinite loop
                if (line.contains("cc.kowx712.wishexport") ||
                    line.contains(LOGCAT_CAPTURE_SERVICE) ||
                    line.contains(ROOT_LOGCAT_TAG) ||
                    line.contains(SHIZUKU_LOGCAT_TAG)) {
                    continue
                }

                // Try to match against all supported games
                val url = extractWishUrl(line)
                if (url != null) {
                    return url
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error reading logcat", e)
        }

        return null
    }

    /**
     * Extracts wish URL from a logcat line if it matches any game pattern.
     */
    protected fun extractWishUrl(line: String): String? {
        for (game in GameConfig.SUPPORTED_GAMES) {
            val matchResult = game.urlPattern.find(line)
            if (matchResult != null) {
                return matchResult.value
            }
        }
        return null
    }
}

/**
 * Logcat capture implementation using Shizuku with AIDL.
 */
class ShizukuLogcatService : LogcatCaptureService() {

    companion object {
        private const val TAG = SHIZUKU_LOGCAT_TAG
    }

    private val serviceConnection = ShizukuServiceConnection()
    private var bindFailureLogged = false
    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(
            "cc.kowx712.wishexport",
            ShizukuLogcatUserService::class.java.name
        )
    ).daemon(false).processNameSuffix("logcat").debuggable(true).version(2)

    private fun bindService(): Boolean {
        return try {
            if (!serviceConnection.isConnected()) {
                Shizuku.bindUserService(userServiceArgs, serviceConnection)
                // Binding is asynchronous; wait for the callback.
                val deadline = System.currentTimeMillis() + 3000L
                while (!serviceConnection.isConnected() && System.currentTimeMillis() < deadline) {
                    Thread.sleep(50)
                }
            }
            serviceConnection.isConnected().also { connected ->
                if (connected) bindFailureLogged = false
            }
        } catch (e: Exception) {
            if (!bindFailureLogged) {
                Log.e(TAG, "Failed to bind service", e)
                bindFailureLogged = true
            }
            false
        }
    }

    override suspend fun clearLogcat() {
        withContext(Dispatchers.IO) {
            try {
                if (bindService()) {
                    serviceConnection.getBinder()?.clearLogcat()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear logcat", e)
            }
        }
    }

    override suspend fun captureWishUrl(): String? = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + 600000L
        var binder = serviceConnection.getBinder()
        var readFailureLogged = false
        while (System.currentTimeMillis() < deadline) {
            try {
                if (binder == null || !serviceConnection.isConnected()) {
                    bindService()
                    binder = serviceConnection.getBinder()
                    if (binder == null) {
                        Thread.sleep(500)
                        continue
                    }
                }
                // Keep the Binder response small; large log dumps can exceed the
                val output = binder.executeLogcat(arrayOf("/system/bin/logcat", "-d", "-v", "raw", "-t", "40", "*:*"))
                readFailureLogged = false
                output.lineSequence().forEach { line ->
                    if (!line.contains("cc.kowx712.wishexport")) {
                        extractWishUrl(line)?.let { return@withContext it }
                    }
                }
            } catch (e: Exception) {
                if (!readFailureLogged) {
                    Log.e(TAG, "Failed to read logcat", e)
                    readFailureLogged = true
                }
                binder = null
                Thread.sleep(500)
            }
            Thread.sleep(500)
        }
        null
    }

    override fun startLogcatProcess(): Process {
        if (!bindService()) {
            throw IllegalStateException("Unable to connect to Shizuku user service")
        }
        val binder = serviceConnection.getBinder()
            ?: throw IllegalStateException("Shizuku logcat service is unavailable")
        val descriptor = binder.startLogcat(arrayOf("logcat", "-v", "raw", "*:*"))
        return ShizukuLogcatProcess(descriptor, binder)
    }
}

private class ShizukuLogcatProcess(
    descriptor: ParcelFileDescriptor,
    private val service: IShizukuLogcatService
) : Process() {
    private val input = ParcelFileDescriptor.AutoCloseInputStream(descriptor)
    private val error = ByteArrayInputStream(ByteArray(0))
    @Volatile private var exited = false

    override fun getInputStream(): InputStream = input
    override fun getErrorStream(): InputStream = error
    override fun getOutputStream(): OutputStream = NULL_OUTPUT_STREAM
    override fun waitFor(): Int {
        try { input.readBytes() } catch (_: Exception) { }
        exited = true
        return 0
    }
    override fun exitValue(): Int {
        if (!exited) throw IllegalThreadStateException("Process has not exited")
        return 0
    }
    override fun destroy() {
        if (exited) return
        exited = true
        try { service.stopLogcat() } catch (_: Exception) { }
        try { input.close() } catch (_: Exception) { }
    }
    override fun isAlive(): Boolean = !exited

    private companion object {
        val NULL_OUTPUT_STREAM: OutputStream = object : OutputStream() {
            override fun write(byte: Int) = Unit
        }
    }
}

/**
 * Logcat capture implementation using root access with LibSU.
 */
class RootLogcatService : LogcatCaptureService() {

    companion object {
        private const val TAG = ROOT_LOGCAT_TAG
    }

    private fun createRootShell(): Shell {
        val builder = Shell.Builder.create()
        return try {
            builder.build("su")
        } catch (_: Throwable) {
            Log.w(TAG, "Root shell unavailable; falling back to sh")
            builder.build("sh")
        }
    }

    private val rootShell: Shell by lazy {
        createRootShell()
    }

    override suspend fun clearLogcat() {
        withContext(Dispatchers.IO) {
            try {
                val result = rootShell.newJob().add("logcat -c").exec()
                if (!result.isSuccess) {
                    Log.e(TAG, "Failed to clear logcat")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear logcat", e)
            }
        }
    }

    override fun startLogcatProcess(): Process {
        return ProcessBuilder("su", "-c", "logcat", "-v", "raw", "*:*")
            .redirectErrorStream(true)
            .start()
    }
}

/**
 * Factory for creating the appropriate logcat capture service.
 */
object LogcatServiceFactory {
    fun create(accessMode: AccessMode): LogcatCaptureService {
        return when (accessMode) {
            AccessMode.SHIZUKU -> ShizukuLogcatService()
            AccessMode.ROOT -> RootLogcatService()
        }
    }
}
