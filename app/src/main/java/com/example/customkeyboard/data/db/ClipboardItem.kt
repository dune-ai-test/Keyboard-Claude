package com.example.customkeyboard.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single clipboard history entry. Content is stored purely locally (Room/SQLite on-device
 * database) — never transmitted anywhere, satisfying the app's privacy requirements.
 */
@Entity(tableName = "clipboard_items")
data class ClipboardItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val previewLabel: String = text.take(60)
)
