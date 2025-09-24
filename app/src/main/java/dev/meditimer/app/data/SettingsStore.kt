package dev.meditimer.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsData by preferencesDataStore("meditimer_settings")

data class Settings(
    val startGong: Int,           // 1..3 (eingebaute Sounds)
    val endGong: Int,             // 1..3 (eingebaute Sounds)
    val fullscreen: Boolean,
    val keepAwake: Boolean,
    val forceAlarmStream: Boolean,
    val countUp: Boolean,

    // Benutzerdefinierte Audiodateien (optional)
    val useCustomStart: Boolean,
    val useCustomEnd: Boolean,
    val startUri: String?,        // content://...
    val endUri: String?,          // content://...

    // NEU: zuletzt verwendete Werte (für Startseite & Default-Start)
    val lastWarmupSec: Int,
    val lastMeditateSec: Int
)

class SettingsStore(private val context: Context) {
    private val KEY_START = intPreferencesKey("start_gong")
    private val KEY_END   = intPreferencesKey("end_gong")
    private val KEY_FULL  = booleanPreferencesKey("fullscreen")
    private val KEY_AWAKE = booleanPreferencesKey("keep_awake")
    private val KEY_FORCE = booleanPreferencesKey("force_alarm_stream")
    private val KEY_UP    = booleanPreferencesKey("count_up")

    private val KEY_USE_CUSTOM_START = booleanPreferencesKey("use_custom_start")
    private val KEY_USE_CUSTOM_END   = booleanPreferencesKey("use_custom_end")
    private val KEY_START_URI        = stringPreferencesKey("start_uri")
    private val KEY_END_URI          = stringPreferencesKey("end_uri")

    private val KEY_LAST_WARM        = intPreferencesKey("last_warmup_sec")
    private val KEY_LAST_MEDIT       = intPreferencesKey("last_meditate_sec")

    val flow: Flow<Settings> = context.settingsData.data.map { p ->
        Settings(
            startGong = p[KEY_START] ?: 1,
            endGong = p[KEY_END] ?: 2,
            fullscreen = p[KEY_FULL] ?: false,
            keepAwake = p[KEY_AWAKE] ?: false,
            forceAlarmStream = p[KEY_FORCE] ?: true,
            countUp = p[KEY_UP] ?: false,
            useCustomStart = p[KEY_USE_CUSTOM_START] ?: false,
            useCustomEnd   = p[KEY_USE_CUSTOM_END] ?: false,
            startUri = p[KEY_START_URI],
            endUri   = p[KEY_END_URI],
            // sinnvolle Default-Startwerte, falls noch nie gestartet:
            lastWarmupSec = p[KEY_LAST_WARM] ?: 60,
            lastMeditateSec = p[KEY_LAST_MEDIT] ?: 25*60
        )
    }

    suspend fun save(s: Settings) {
        context.settingsData.edit { e ->
            e[KEY_START] = s.startGong
            e[KEY_END] = s.endGong
            e[KEY_FULL] = s.fullscreen
            e[KEY_AWAKE] = s.keepAwake
            e[KEY_FORCE] = s.forceAlarmStream
            e[KEY_UP] = s.countUp
            e[KEY_USE_CUSTOM_START] = s.useCustomStart
            e[KEY_USE_CUSTOM_END]   = s.useCustomEnd
            if (s.startUri != null) e[KEY_START_URI] = s.startUri else e.remove(KEY_START_URI)
            if (s.endUri   != null) e[KEY_END_URI]   = s.endUri   else e.remove(KEY_END_URI)
            e[KEY_LAST_WARM] = s.lastWarmupSec
            e[KEY_LAST_MEDIT] = s.lastMeditateSec
        }
    }

    // Bequemer Helfer: nur „zuletzt verwendet“ aktualisieren
    suspend fun saveLastUsed(warmupSec: Int, meditateSec: Int) {
        context.settingsData.edit { e ->
            e[KEY_LAST_WARM] = warmupSec
            e[KEY_LAST_MEDIT] = meditateSec
        }
    }
}
