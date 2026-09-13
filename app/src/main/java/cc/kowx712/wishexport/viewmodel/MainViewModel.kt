package cc.kowx712.wishexport.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.kowx712.wishexport.data.PreferencesManager
import cc.kowx712.wishexport.model.AccessMode
import cc.kowx712.wishexport.model.CaptureState
import cc.kowx712.wishexport.service.LogcatServiceFactory
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/**
 * ViewModel for the main screen.
 * Manages capture state and coordinates with the logcat service.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val preferencesManager = PreferencesManager(application)

    private val _captureState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    private val _accessMode = MutableStateFlow<AccessMode?>(null)
    val accessMode: StateFlow<AccessMode?> = _accessMode.asStateFlow()

    init {
        // Load saved access mode on startup
        viewModelScope.launch {
            preferencesManager.accessMode.collect { savedMode ->
                if (_accessMode.value == null && savedMode != null) {
                    _accessMode.value = savedMode
                }
            }
        }
    }

    /**
     * Sets the access mode (Shizuku or Root) and saves it.
     */
    fun setAccessMode(mode: AccessMode) {
        _accessMode.value = mode
        _captureState.value = CaptureState.Idle
        viewModelScope.launch {
            preferencesManager.saveAccessMode(mode)
        }
    }

    /**
     * Checks if the selected access mode is available.
     */
    fun checkAccessModeAvailability(): Boolean {
        return when (_accessMode.value) {
            AccessMode.SHIZUKU -> checkShizukuAvailability()
            AccessMode.ROOT -> checkRootAvailability()
            null -> false
        }
    }

    /**
     * Starts capturing wish URLs from logcat.
     */
    fun startCapture() {
        val mode = _accessMode.value
        if (mode == null) {
            _captureState.value = CaptureState.Error("Access mode not set")
            return
        }

        if (!checkAccessModeAvailability()) {
            val errorMessage = when (mode) {
                AccessMode.SHIZUKU -> "Shizuku is not running. Please start Shizuku and try again."
                AccessMode.ROOT -> "Root access is not available on this device."
            }
            _captureState.value = CaptureState.Error(errorMessage)
            return
        }

        viewModelScope.launch {
            try {
                _captureState.value = CaptureState.Capturing
                val service = LogcatServiceFactory.create(mode)
                val url = service.captureWishUrl()

                if (url != null) {
                    _captureState.value = CaptureState.Success(url)
                } else {
                    _captureState.value = CaptureState.Error(
                        "No wish URL detected. Please open the wish history in your game and try again."
                    )
                }
            } catch (e: Exception) {
                _captureState.value = CaptureState.Error("An error occurred: ${e.message}")
                Log.e(TAG, "Capture error", e)
            }
        }
    }

    /**
     * Stops the current capture operation.
     */
    fun stopCapture() {
        if (_captureState.value is CaptureState.Capturing) {
            _captureState.value = CaptureState.Idle
        }
    }

    /**
     * Resets the capture state to idle.
     */
    fun resetState() {
        _captureState.value = CaptureState.Idle
    }

    /**
     * Clears the saved access mode (for permission denied scenario).
     */
    fun clearAccessMode() {
        _accessMode.value = null
        _captureState.value = CaptureState.Idle
    }

    /**
     * Copies the given text to the clipboard.
     */
    fun copyToClipboard(text: String) {
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Wish URL", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun checkShizukuAvailability(): Boolean {
        return try {
            // First check if Shizuku binder is running
            if (!Shizuku.pingBinder()) {
                return false
            }
            // Then check permission
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    private fun checkRootAvailability(): Boolean {
        return try {
            Shell.getShell().isRoot()
        } catch (_: Exception) {
            false
        }
    }
}
