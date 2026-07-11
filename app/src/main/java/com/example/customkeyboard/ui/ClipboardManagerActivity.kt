package com.example.customkeyboard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.customkeyboard.data.db.ClipboardItem
import com.example.customkeyboard.ui.theme.CustomKeyboardTheme
import com.example.customkeyboard.viewmodel.ClipboardViewModel

class ClipboardManagerActivity : ComponentActivity() {

    private val viewModel: ClipboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CustomKeyboardTheme {
                val history by viewModel.history.collectAsState()
                ClipboardScreen(
                    items = history,
                    onCopy = { copyToSystemClipboard(it.text) },
                    onTogglePin = { viewModel.togglePin(it) },
                    onDelete = { viewModel.delete(it) },
                    onClearUnpinned = { viewModel.clearUnpinned() },
                    onBack = { finish() }
                )
            }
        }
    }

    private fun copyToSystemClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("clip", text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipboardScreen(
    items: List<ClipboardItem>,
    onCopy: (ClipboardItem) -> Unit,
    onTogglePin: (ClipboardItem) -> Unit,
    onDelete: (ClipboardItem) -> Unit,
    onClearUnpinned: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clipboard Manager") },
                actions = { TextButton(onClick = onClearUnpinned) { Text("Clear") } }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No clipboard history yet", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(items, key = { it.id }) { item ->
                    ClipboardRow(item, onCopy, onTogglePin, onDelete)
                }
            }
        }
    }
}

@Composable
private fun ClipboardRow(
    item: ClipboardItem,
    onCopy: (ClipboardItem) -> Unit,
    onTogglePin: (ClipboardItem) -> Unit,
    onDelete: (ClipboardItem) -> Unit
) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(0.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = item.text,
                maxLines = 3,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            Row {
                IconButton(onClick = { onCopy(item) }) {
                    Text("Paste")
                }
                IconButton(onClick = { onTogglePin(item) }) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = "Pin",
                        tint = if (item.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onDelete(item) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}
