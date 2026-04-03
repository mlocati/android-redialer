package it.locati.michele.redialer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "redial_prefs")

class RedialPreferences(private val context: Context) {

    companion object {
        val DELAY_SECONDS = intPreferencesKey("delay_seconds")
        val STOP_THRESHOLD_SECONDS = intPreferencesKey("stop_threshold_seconds")
        
        const val DEFAULT_DELAY_SECONDS = 10
        const val DEFAULT_STOP_THRESHOLD_SECONDS = 2
    }

    val delaySeconds: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[DELAY_SECONDS] ?: DEFAULT_DELAY_SECONDS
    }

    val stopThresholdSeconds: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[STOP_THRESHOLD_SECONDS] ?: DEFAULT_STOP_THRESHOLD_SECONDS
    }

    suspend fun saveDelaySeconds(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[DELAY_SECONDS] = seconds
        }
    }

    suspend fun saveStopThresholdSeconds(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[STOP_THRESHOLD_SECONDS] = seconds
        }
    }
}
