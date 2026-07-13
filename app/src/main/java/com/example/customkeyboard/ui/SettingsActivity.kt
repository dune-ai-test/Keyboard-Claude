package com.example.customkeyboard.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.customkeyboard.R
import com.example.customkeyboard.data.IMAGE_THEME_ID
import com.example.customkeyboard.data.KeyboardSettings
import com.example.customkeyboard.data.KeyboardThemeColors
import com.example.customkeyboard.data.KeyboardThemePresets
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
                else -> isSystemInDarkTheme()
            }

            val imagePicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                if (uri != null) {
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: SecurityException) {
                        // Some providers don't support persistable grants; the image still works
                        // for this session even if it won't survive a reboot.
                    }
                    viewModel.setKeyboardImage(uri.toString())
                }
            }

            CustomKeyboardTheme(darkTheme = darkOverride) {
                SettingsScreen(
                    settings = settings,
                    onEnableKeyboard = { openImeSettings() },
                    onSwitchKeyboard = { showImePicker() },
                    onOpenClipboard = { startActivity(Intent(this, ClipboardManagerActivity::class.java)) },
                    onAppThemeChange = viewModel::setTheme,
                    onKeyboardThemeChange = viewModel::setKeyboardTheme,
                    onPickImage = { imagePicker.launch(arrayOf("image/*")) },
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
    onAppThemeChange: (String) -> Unit,
    onKeyboardThemeChange: (String) -> Unit,
    onPickImage: () -> Unit,
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
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
        ) {
            item {
                HeroCard(onEnableKeyboard = onEnableKeyboard, onSwitchKeyboard = onSwitchKeyboard)
            }

            item {
                OutlinedButton(
                    onClick = onOpenClipboard,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Clipboard Manager")
                }
            }

            item { SectionHeader("Keyboard Theme", Icons.Filled.Palette) }
            item {
                SettingsCard {
                    Text(
                        "Pick a color palette, or use your own photo as the keyboard background.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.size(4.dp))
                    ThemeSwatchRow(
                        selectedId = settings.keyboardThemeId,
                        onSelect = onKeyboardThemeChange,
                        onPickImage = onPickImage
                    )
                }
            }

            item { SectionHeader("App Appearance", Icons.Filled.Palette) }
            item {
                SettingsCard {
                    Text("App theme", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppThemeChip("System", settings.theme == KeyboardSettings.THEME_SYSTEM) { onAppThemeChange(KeyboardSettings.THEME_SYSTEM) }
                        AppThemeChip("Light", settings.theme == KeyboardSettings.THEME_LIGHT) { onAppThemeChange(KeyboardSettings.THEME_LIGHT) }
                        AppThemeChip("Dark", settings.theme == KeyboardSettings.THEME_DARK) { onAppThemeChange(KeyboardSettings.THEME_DARK) }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(
                        "Keyboard height — ${(settings.keyboardHeightPercent * 100).toInt()}%",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(value = settings.keyboardHeightPercent, onValueChange = onHeightChange, valueRange = 0.8f..1.3f)
                }
            }

            item { SectionHeader("Feedback", Icons.Filled.Bolt) }
            item {
                SettingsCard {
                    SwitchRow("Vibration on keypress", settings.vibrationEnabled, onVibrationToggle)
                    if (settings.vibrationEnabled) {
                        Text("Strength — ${settings.vibrationStrength}ms", style = MaterialTheme.typography.bodySmall)
                        Slider(value = settings.vibrationStrength.toFloat(), onValueChange = onVibrationStrengthChange, valueRange = 5f..80f)
                    }
                    SwitchRow("Key sounds", settings.soundEnabled, onSoundToggle)
                    if (settings.soundEnabled) {
                        Text("Volume — ${(settings.soundVolume * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                        Slider(value = settings.soundVolume, onValueChange = onSoundVolumeChange, valueRange = 0f..1f)
                    }
                }
            }

            item { SectionHeader("Typing Intelligence", Icons.Filled.Psychology) }
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

            item { SectionHeader("Privacy", Icons.Filled.Lock) }
            item {
                SettingsCard {
                    Text(stringResource(R.string.privacy_notice), style = MaterialTheme.typography.bodySmall)
                }
            }

            item { Spacer(Modifier.size(8.dp)) }
        }
    }
}

@Composable
private fun HeroCard(onEnableKeyboard: () -> Unit, onSwitchKeyboard: () -> Unit) {
    val gradient = Brush.linearGradient(
        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
    )
    Card(shape = MaterialTheme.shapes.large, elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Keyboard, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.size(14.dp))
            Text("Custom Keyboard", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(4.dp))
            Text(
                "Smart, private, fully on-device typing.",
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.size(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onEnableKeyboard,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) { Text("Enable", fontWeight = FontWeight.SemiBold) }

                OutlinedButton(
                    onClick = onSwitchKeyboard,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) { Text("Switch") }
            }
        }
    }
}

@Composable
private fun ThemeSwatchRow(selectedId: String, onSelect: (String) -> Unit, onPickImage: () -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(KeyboardThemePresets.ALL) { theme ->
            ThemeSwatch(theme = theme, selected = selectedId == theme.id, onClick = { onSelect(theme.id) })
        }
        item {
            ImageSwatch(selected = selectedId == IMAGE_THEME_ID, onClick = onPickImage)
        }
    }
}

@Composable
private fun ThemeSwatch(theme: KeyboardThemeColors, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color(theme.swatch))
                .border(
                    width = if (selected) 3.dp else 0.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                    shape = CircleShape
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color(theme.keyText))
            }
        }
        Spacer(Modifier.size(6.dp))
        Text(
            theme.displayName.substringBefore(" "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ImageSwatch(selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Custom image theme")
        }
        Spacer(Modifier.size(6.dp))
        Text("Photo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(shape = MaterialTheme.shapes.medium, elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun AppThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}
