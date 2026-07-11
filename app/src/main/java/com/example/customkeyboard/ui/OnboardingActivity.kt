package com.example.customkeyboard.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.customkeyboard.ui.theme.CustomKeyboardTheme

/** Minimal first-run screen guiding the user to enable & switch to the keyboard. */
class OnboardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CustomKeyboardTheme {
                Scaffold { padding ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
                    ) {
                        Text("Welcome", style = MaterialTheme.typography.headlineMedium)
                        Text("To start typing with Custom Keyboard, enable it in system settings, then select it as your active keyboard.")
                        Button(onClick = { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }) {
                            Text("Open Keyboard Settings")
                        }
                        Button(onClick = { finish() }) { Text("Done") }
                    }
                }
            }
        }
    }
}
