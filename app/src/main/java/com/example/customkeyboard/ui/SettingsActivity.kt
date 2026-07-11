package com.example.customkeyboard.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.customkeyboard.R
import com.example.customkeyboard.data.KeyboardSettings
import com.example.customkeyboard.ui.theme.CustomKeyboardTheme
import com.example.customkeyboard.viewmodel.SettingsViewModel

class SettingsActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by viewModel.settings.collectAsState()
            val darkOverride = when (settings.theme) {
                KeyboardSettings.THEME_DARK -> true
                KeyboardSettings.THEME_LIGHT -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            CustomKeyboardTheme(darkTheme = darkOverride) {
                SettingsScreen(
                    settings = settings,
                    onEnableKeyboard = { openImeSettings() },
                    onSwitchKeyboard = { showImePicker() },
                    onOpenClipboard = { startActivity(Intent(this, ClipboardManagerActivity::class.java)) },
                    onThemeChange = viewModel::setTheme,
                    onHeightChange = viewModel::setKeyboardHeight,
                    onVibrationToggle = viewModel::setVibrationEnabled,
                    onVibrationStrengthChange = { viewModel.setVibrationStrength(it.toInt()) },
                    onSoundToggle = viewModel::setSoundEnabled,
                    onSoundVolumeChange = viewModel::setSoundVolume,
                    onPredictionToggle = viewModel::setPredictionEnabled,
                    onAutoCorrectToggle = viewModel::setAutoCorrectEnabled,
                    onSwipeToggle = viewModel::setSwipeEnabled,
                    onVoiceToggle = viewModel::setVoiceEnabled,
                    onAutoCapsToggle = viewModel::setAutoCapitalize,
                    onDoubleSpaceToggle = viewModel::setDoubleSpacePeriod
                )
            }
        }
    }

    private fun openImeSettings() {
        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    }

    private fun showImePicker() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: KeyboardSettings,
    onEnableKeyboard: () -> Unit,
    onSwitchKeyboard: () -> Unit,
    onOpenClipboard: () -> Unit,
    onThemeChange: (String) -> Unit,
    onHeightChange: (Float) -> Unit,
    onVibrationToggle: (Boolean) -> Unit,
    onVibrationStrengthChange: (Float) -> Unit,
    onSoundToggle: (Boolean) -> Unit,
    onSoundVolumeChange: (Float) -> Unit,
    onPredictionToggle: (Boolean) -> Unit,
    onAutoCorrectToggle: (Boolean) -> Unit,
    onSwipeToggle: (Boolean) -> Unit,
    onVoiceToggle: (Boolean) -> Unit,
    onAutoCapsToggle: (Boolean) -> Unit,
    onDoubleSpaceToggle: (Boolean) -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
        ) {
            item {
                Card(colors = CardDefaults.cardColors()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Setup", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onEnableKeyboard, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Keyboard, contentDescription = null)
                                Spacer(Modifier.height(0.dp))
                                Text(" Enable")
                            }
                            OutlinedButton(onClick = onSwitchKeyboard, modifier = Modifier.weight(1f)) {
                                Text("Switch Keyboard")
                            }
                        }
                        OutlinedButton(onClick = onOpenClipboard, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.ContentPaste, contentDescription = null)
                            Text("  Clipboard Manager")
                        }
                    }
                }
            }

            item { SectionHeader("Appearance") }
            item {
                SettingsCard {
                    Text("Theme", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ThemeOption("System", settings.theme == KeyboardSettings.THEME_SYSTEM) { onThemeChange(KeyboardSettings.THEME_SYSTEM) }
                        ThemeOption("Light", settings.theme == KeyboardSettings.THEME_LIGHT) { onThemeChange(KeyboardSettings.THEME_LIGHT) }
                        ThemeOption("Dark", settings.theme == KeyboardSettings.THEME_DARK) { onThemeChange(KeyboardSettings.THEME_DARK) }
                    }
                    Divider(Modifier.padding(vertical = 8.dp))
                    Text("Keyboard height: ${(settings.keyboardHeightPercent * 100).toInt()}%")
                    Slider(
                        value = settings.keyboardHeightPercent,
                        onValueChange = onHeightChange,
                        valueRange = 0.8f..1.3f
                    )
                }
            }

            item { SectionHeader("Feedback") }
            item {
                SettingsCard {
                    SwitchRow("Vibration on keypress", settings.vibrationEnabled, onVibrationToggle)
                    if (settings.vibrationEnabled) {
                        Text("Vibration strength: ${settings.vibrationStrength}ms")
                        Slider(
                            value = settings.vibrationStrength.toFloat(),
                            onValueChange = onVibrationStrengthChange,
                            valueRange = 5f..80f
                        )
                    }
                    SwitchRow("Key sounds", settings.soundEnabled, onSoundToggle)
                    if (settings.soundEnabled) {
                        Text("Sound volume: ${(settings.soundVolume * 100).toInt()}%")
                        Slider(
                            value = settings.soundVolume,
                            onValueChange = onSoundVolumeChange,
                            valueRange = 0f..1f
                        )
                    }
                }
            }

            item { SectionHeader("Typing Intelligence") }
            item {
                SettingsCard {
                    SwitchRow("Word prediction", settings.predictionEnabled, onPredictionToggle)
                    SwitchRow("Auto-correct", settings.autoCorrectEnabled, onAutoCorrectToggle)
                    SwitchRow("Swipe (gesture) typing", settings.swipeTypingEnabled, onSwipeToggle)
                    SwitchRow("Voice typing", settings.voiceTypingEnabled, onVoiceToggle)
                    SwitchRow("Auto-capitalize sentences", settings.autoCapitalize, onAutoCapsToggle)
                    SwitchRow("Double-space inserts period", settings.doubleSpacePeriod, onDoubleSpaceToggle)
                }
            }

            item { SectionHeader("Privacy") }
            item {
                SettingsCard {
                    Text(
                        stringResource(R.string.privacy_notice),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = onClick)
        Text(label)
        Spacer(Modifier.height(0.dp))
    }
}
