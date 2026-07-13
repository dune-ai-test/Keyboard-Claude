package com.example.customkeyboard.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.customkeyboard.data.KeyboardSettings
import com.example.customkeyboard.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** MVVM ViewModel backing the Settings screen; also the same repository the IME reads from live. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository.getInstance(application)

    val settings: StateFlow<KeyboardSettings> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = KeyboardSettings()
    )

    fun setTheme(theme: String) = viewModelScope.launch { repository.setTheme(theme) }
    fun setKeyboardHeight(percent: Float) = viewModelScope.launch { repository.setKeyboardHeight(percent) }
    fun setVibrationEnabled(enabled: Boolean) = viewModelScope.launch { repository.setVibrationEnabled(enabled) }
    fun setVibrationStrength(ms: Int) = viewModelScope.launch { repository.setVibrationStrength(ms) }
    fun setSoundEnabled(enabled: Boolean) = viewModelScope.launch { repository.setSoundEnabled(enabled) }
    fun setSoundVolume(volume: Float) = viewModelScope.launch { repository.setSoundVolume(volume) }
    fun setPredictionEnabled(enabled: Boolean) = viewModelScope.launch { repository.setPredictionEnabled(enabled) }
    fun setAutoCorrectEnabled(enabled: Boolean) = viewModelScope.launch { repository.setAutoCorrectEnabled(enabled) }
    fun setSwipeEnabled(enabled: Boolean) = viewModelScope.launch { repository.setSwipeEnabled(enabled) }
    fun setVoiceEnabled(enabled: Boolean) = viewModelScope.launch { repository.setVoiceEnabled(enabled) }
    fun setPopupEnabled(enabled: Boolean) = viewModelScope.launch { repository.setPopupEnabled(enabled) }
    fun setAutoCapitalize(enabled: Boolean) = viewModelScope.launch { repository.setAutoCapitalize(enabled) }
    fun setDoubleSpacePeriod(enabled: Boolean) = viewModelScope.launch { repository.setDoubleSpacePeriod(enabled) }
    fun setKeyBordersEnabled(enabled: Boolean) = viewModelScope.launch { repository.setKeyBordersEnabled(enabled) }
    fun setKeyboardTheme(themeId: String) = viewModelScope.launch { repository.setKeyboardTheme(themeId) }
    fun setKeyboardImage(uri: String) = viewModelScope.launch { repository.setKeyboardImage(uri) }
}
