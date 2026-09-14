package cc.kowx712.wishexport.service

import android.content.ComponentName
import android.util.Log
import cc.kowx712.wishexport.model.AccessMode
import cc.kowx712.wishexport.model.GameConfig
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.minutes

private const val LOGCAT_CAPTURE_SERVICE = "LogcatCaptureService"
private const val SHIZUKU_LOGCAT_TAG = "ShizukuLogcatService"
private const val ROOT_LOGCAT_TAG = "RootLogcatService"

/**
 * Abstract base class for logcat capture services.
 * Implements the Template Method pattern for extensibility.
 */
abstract class LogcatCaptureService {

    @Volatile
    private var activeProcess: Process? = null

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
            activeProcess = process
            val url = readLogcatOutput(process)
            url
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing wish URL", e)
            null
        } finally {
            activeProcess?.destroy()
            activeProcess = null
        }
    }

    open fun stopCapture() {
        activeProcess?.destroy()
        activeProcess = null
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
        for (config in GameConfig.SUPPORTED_CONFIGS) {
            val matchResult = config.urlPattern.find(line)
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
    ).daemon(false).processNameSuffix("logcat").debuggable(true).version(4)

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

    override suspend fun clearLogcat() = Unit

    override suspend fun captureWishUrl(): String? = withContext(Dispatchers.IO) {
        if (!bindService()) throw IllegalStateException("Unable to connect to Shizuku user service")
        val binder = serviceConnection.getBinder()
            ?: throw IllegalStateException("Shizuku logcat service is unavailable")
        try {
            withTimeoutOrNull(10.minutes) {
                suspendCancellableCoroutine { continuation ->
                    val completed = AtomicBoolean(false)
                    val callback = object : IShizukuLogcatCallback.Stub() {
                        override fun onUrlFound(url: String) {
                            if (completed.compareAndSet(false, true)) continuation.resume(url)
                        }
                        override fun onCaptureError(message: String) {
                            if (completed.compareAndSet(false, true)) {
                                continuation.resumeWithException(IllegalStateException(message))
                            }
                        }
                        override fun onCaptureFinished() {
                            if (completed.compareAndSet(false, true)) continuation.resume(null)
                        }
                    }
                    continuation.invokeOnCancellation {
                        completed.set(true)
                        try { binder.stopCapture() } catch (_: Exception) { }
                    }
                    try {
                        binder.startCapture(callback)
                    } catch (e: Exception) {
                        if (completed.compareAndSet(false, true)) continuation.resumeWithException(e)
                    }
                }
            }
        } finally {
            try { binder.stopCapture() } catch (_: Exception) { }
        }
    }

    override fun stopCapture() {
        try { serviceConnection.getBinder()?.stopCapture() } catch (_: Exception) { }
    }

    override fun startLogcatProcess(): Process =
        throw UnsupportedOperationException("Shizuku capture runs in UserService")
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
