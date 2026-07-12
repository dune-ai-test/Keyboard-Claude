package com.example.customkeyboard.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.customkeyboard.data.db.ClipboardItem
import com.example.customkeyboard.data.db.ClipboardRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** MVVM ViewModel exposing clipboard history state to both the IME suggestion strip and the UI. */
class ClipboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ClipboardRepository.getInstance(application)

    val history: StateFlow<List<ClipboardItem>> = repository.history.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun addClip(text: String) = viewModelScope.launch { repository.addOrUpdate(text) }

    fun togglePin(item: ClipboardItem) = viewModelScope.launch { repository.togglePin(item) }

    fun delete(item: ClipboardItem) = viewModelScope.launch { repository.delete(item) }

    fun clearUnpinned() = viewModelScope.launch { repository.clearUnpinned() }

    fun clearAll() = viewModelScope.launch { repository.clearAll() }

    fun reorder(items: List<ClipboardItem>) = viewModelScope.launch { repository.reorder(items) }
}
