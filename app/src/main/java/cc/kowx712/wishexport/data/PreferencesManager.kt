package cc.kowx712.wishexport.data

import android.content.Context
import android.content.SharedPreferences
import cc.kowx712.wishexport.model.AccessMode
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Manages persistent preferences using SharedPreferences.
 */
class PreferencesManager(private val context: Context) {

    companion object {
        private const val PREFERENCES_NAME = "settings"
        private const val ACCESS_MODE_KEY = "access_mode"
    }

    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * Flow of the saved access mode, or null if not set.
     */
    val accessMode: Flow<AccessMode?> = callbackFlow {
        fun emitAccessMode() {
            trySend(readAccessMode())
        }

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == ACCESS_MODE_KEY) {
                emitAccessMode()
            }
        }

        emitAccessMode()
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /**
     * Saves the selected access mode.
     */
    suspend fun saveAccessMode(mode: AccessMode) {
        preferences.edit().putString(ACCESS_MODE_KEY, mode.name).apply()
    }

    private fun readAccessMode(): AccessMode? {
        return preferences.getString(ACCESS_MODE_KEY, null)?.let { modeName ->
            runCatching { AccessMode.valueOf(modeName) }.getOrNull()
        }
    }
}
