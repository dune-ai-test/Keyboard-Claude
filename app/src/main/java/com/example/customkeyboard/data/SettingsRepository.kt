package com.example.customkeyboard.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "keyboard_settings")

/** Immutable snapshot of all user-configurable keyboard settings. */
data class KeyboardSettings(
    val theme: String = THEME_SYSTEM,
    val keyboardHeightPercent: Float = 1.0f,   // 0.8 - 1.3 scale of default height
    val vibrationEnabled: Boolean = true,
    val vibrationStrength: Int = 40,           // ms
    val soundEnabled: Boolean = false,
    val soundVolume: Float = 0.5f,
    val predictionEnabled: Boolean = true,
    val autoCorrectEnabled: Boolean = true,
    val swipeTypingEnabled: Boolean = true,
    val voiceTypingEnabled: Boolean = true,
    val popupOnKeyPress: Boolean = true,
    val autoCapitalize: Boolean = true,
    val doubleSpacePeriod: Boolean = true
) {
    companion object {
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val THEME_SYSTEM = "system"
    }
}

/**
 * Repository that exposes keyboard settings as a reactive [Flow] (observed by both the IME
 * service and the Settings UI) and persists them via Jetpack DataStore. All reads/writes happen
 * on local device storage only — no network I/O, satisfying the app's privacy requirements.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val HEIGHT = floatPreferencesKey("keyboard_height_percent")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val VIBRATION_STRENGTH = intPreferencesKey("vibration_strength")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val SOUND_VOLUME = floatPreferencesKey("sound_volume")
        val PREDICTION_ENABLED = booleanPreferencesKey("prediction_enabled")
        val AUTOCORRECT_ENABLED = booleanPreferencesKey("autocorrect_enabled")
        val SWIPE_ENABLED = booleanPreferencesKey("swipe_enabled")
        val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        val POPUP_ENABLED = booleanPreferencesKey("popup_enabled")
        val AUTO_CAPS = booleanPreferencesKey("auto_caps")
        val DOUBLE_SPACE_PERIOD = booleanPreferencesKey("double_space_period")
    }

    val settingsFlow: Flow<KeyboardSettings> = context.dataStore.data.map { prefs ->
        KeyboardSettings(
            theme = prefs[Keys.THEME] ?: KeyboardSettings.THEME_SYSTEM,
            keyboardHeightPercent = prefs[Keys.HEIGHT] ?: 1.0f,
            vibrationEnabled = prefs[Keys.VIBRATION_ENABLED] ?: true,
            vibrationStrength = prefs[Keys.VIBRATION_STRENGTH] ?: 40,
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: false,
            soundVolume = prefs[Keys.SOUND_VOLUME] ?: 0.5f,
            predictionEnabled = prefs[Keys.PREDICTION_ENABLED] ?: true,
            autoCorrectEnabled = prefs[Keys.AUTOCORRECT_ENABLED] ?: true,
            swipeTypingEnabled = prefs[Keys.SWIPE_ENABLED] ?: true,
            voiceTypingEnabled = prefs[Keys.VOICE_ENABLED] ?: true,
            popupOnKeyPress = prefs[Keys.POPUP_ENABLED] ?: true,
            autoCapitalize = prefs[Keys.AUTO_CAPS] ?: true,
            doubleSpacePeriod = prefs[Keys.DOUBLE_SPACE_PERIOD] ?: true
        )
    }

    suspend fun setTheme(theme: String) = context.dataStore.edit { it[Keys.THEME] = theme }
    suspend fun setKeyboardHeight(percent: Float) = context.dataStore.edit { it[Keys.HEIGHT] = percent }
    suspend fun setVibrationEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.VIBRATION_ENABLED] = enabled }
    suspend fun setVibrationStrength(ms: Int) = context.dataStore.edit { it[Keys.VIBRATION_STRENGTH] = ms }
    suspend fun setSoundEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
    suspend fun setSoundVolume(volume: Float) = context.dataStore.edit { it[Keys.SOUND_VOLUME] = volume }
    suspend fun setPredictionEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.PREDICTION_ENABLED] = enabled }
    suspend fun setAutoCorrectEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.AUTOCORRECT_ENABLED] = enabled }
    suspend fun setSwipeEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.SWIPE_ENABLED] = enabled }
    suspend fun setVoiceEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.VOICE_ENABLED] = enabled }
    suspend fun setPopupEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.POPUP_ENABLED] = enabled }
    suspend fun setAutoCapitalize(enabled: Boolean) = context.dataStore.edit { it[Keys.AUTO_CAPS] = enabled }
    suspend fun setDoubleSpacePeriod(enabled: Boolean) = context.dataStore.edit { it[Keys.DOUBLE_SPACE_PERIOD] = enabled }

    companion object {
        @Volatile private var instance: SettingsRepository? = null
        fun getInstance(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
