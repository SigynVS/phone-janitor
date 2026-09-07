package com.sigynvs.phonejanitor.ui.quarantine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sigynvs.phonejanitor.di.AppContainer
import com.sigynvs.phonejanitor.quarantine.QuarantineEntry
import com.sigynvs.phonejanitor.quarantine.QuarantineStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class QuarantineViewModel(private val store: QuarantineStore) : ViewModel() {

    val entries: StateFlow<List<QuarantineEntry>> =
        store.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun restore(entry: QuarantineEntry) = viewModelScope.launch {
        val ok = store.restore(entry)
        _message.value = if (ok) "Restored ${entry.name}" else "Couldn’t restore ${entry.name}"
    }

    fun deleteNow(entry: QuarantineEntry) = viewModelScope.launch {
        store.deleteNow(entry)
        _message.value = "Deleted ${entry.name}"
    }

    fun emptyAll() = viewModelScope.launch {
        val count = entries.value.size
        store.emptyAll()
        _message.value = "Emptied quarantine ($count file(s))"
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { QuarantineViewModel(container.quarantineStore) }
        }
    }
}
