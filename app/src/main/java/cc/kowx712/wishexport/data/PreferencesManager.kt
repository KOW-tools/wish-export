package cc.kowx712.wishexport.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cc.kowx712.wishexport.model.AccessMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Manages persistent preferences using DataStore.
 */
class PreferencesManager(private val context: Context) {

    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
        private val ACCESS_MODE_KEY = stringPreferencesKey("access_mode")
    }

    /**
     * Flow of the saved access mode, or null if not set.
     */
    val accessMode: Flow<AccessMode?> = context.dataStore.data.map { preferences ->
        preferences[ACCESS_MODE_KEY]?.let { modeName ->
            try {
                AccessMode.valueOf(modeName)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    /**
     * Saves the selected access mode.
     */
    suspend fun saveAccessMode(mode: AccessMode) {
        context.dataStore.edit { preferences ->
            preferences[ACCESS_MODE_KEY] = mode.name
        }
    }
}
