package com.example.customkeyboard.data.db

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for clipboard history. Wraps Room DAO calls and enforces a max
 * unpinned-history size to keep storage and battery usage low.
 */
class ClipboardRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).clipboardDao()

    val history: Flow<List<ClipboardItem>> = dao.observeAll()

    suspend fun addOrUpdate(text: String) {
        if (text.isBlank()) return
        val existing = dao.findByText(text)
        if (existing != null) {
            dao.update(existing.copy(timestamp = System.currentTimeMillis()))
        } else {
            dao.insert(ClipboardItem(text = text))
            dao.trimHistory(MAX_UNPINNED_ITEMS)
        }
    }

    suspend fun togglePin(item: ClipboardItem) {
        dao.update(item.copy(isPinned = !item.isPinned))
    }

    suspend fun delete(item: ClipboardItem) = dao.delete(item)

    suspend fun clearUnpinned() = dao.clearUnpinned()

    suspend fun clearAll() = dao.clearAll()

    companion object {
        private const val MAX_UNPINNED_ITEMS = 50

        @Volatile private var instance: ClipboardRepository? = null
        fun getInstance(context: Context): ClipboardRepository =
            instance ?: synchronized(this) {
                instance ?: ClipboardRepository(context.applicationContext).also { instance = it }
            }
    }
}
