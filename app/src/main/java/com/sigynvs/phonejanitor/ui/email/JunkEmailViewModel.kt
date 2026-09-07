package com.sigynvs.phonejanitor.ui.email

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sigynvs.phonejanitor.di.AppContainer
import com.sigynvs.phonejanitor.email.EmailCredentialStore
import com.sigynvs.phonejanitor.email.GmailError
import com.sigynvs.phonejanitor.email.GmailImapClient
import com.sigynvs.phonejanitor.email.MailSummary
import com.sigynvs.phonejanitor.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MailBusy { None, Verifying, Searching, Moving }

data class MailRow(val summary: MailSummary, val selected: Boolean)

data class JunkEmailUiState(
    val loading: Boolean = true,
    val configured: Boolean = false,
    val address: String = "",
    val query: String = SettingsRepository.DEFAULT_GMAIL_QUERY,
    val busy: MailBusy = MailBusy.None,
    val rows: List<MailRow> = emptyList(),
    val searched: Boolean = false,
    val totalMatched: Int = 0,
    val error: String? = null,
    val movedCount: Int? = null,
) {
    val selectedUids: List<Long> get() = rows.filter { it.selected }.map { it.summary.uid }
    val selectedCount: Int get() = rows.count { it.selected }
    val selectedBytes: Long get() = rows.filter { it.selected }.sumOf { it.summary.sizeBytes }
    val allSelected: Boolean get() = rows.isNotEmpty() && rows.all { it.selected }
    val idle: Boolean get() = busy == MailBusy.None
    val hasMoreThanShown: Boolean get() = totalMatched > rows.size
}

class JunkEmailViewModel(
    private val gmail: GmailImapClient,
    private val credentials: EmailCredentialStore,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(JunkEmailUiState())
    val state: StateFlow<JunkEmailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val configured = withContext(Dispatchers.IO) { credentials.isConfigured }
            val addr = withContext(Dispatchers.IO) { credentials.address().orEmpty() }
            val q = settings.gmailQuery.first()
            _state.update {
                it.copy(loading = false, configured = configured, address = addr, query = q)
            }
        }
    }

    fun setQuery(value: String) = _state.update { it.copy(query = value) }

    fun consumeError() = _state.update { it.copy(error = null) }
    fun consumeMoved() = _state.update { it.copy(movedCount = null) }

    fun saveAndVerify(address: String, appPassword: String) {
        if (!_state.value.idle) return
        _state.update { it.copy(busy = MailBusy.Verifying, error = null) }
        viewModelScope.launch {
            val result = runCatching {
                gmail.testConnection(address.trim(), appPassword.replace(" ", ""))
            }
            result.fold(
                onSuccess = {
                    withContext(Dispatchers.IO) { credentials.save(address, appPassword) }
                    _state.update {
                        it.copy(
                            busy = MailBusy.None,
                            configured = true,
                            address = address.trim(),
                            error = null,
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(busy = MailBusy.None, error = messageFor(e)) }
                },
            )
        }
    }

    fun changeAccount() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { credentials.clear() }
            _state.update {
                it.copy(configured = false, rows = emptyList(), searched = false, error = null)
            }
        }
    }

    fun search() {
        val s = _state.value
        if (!s.idle || !s.configured) return
        _state.update { it.copy(busy = MailBusy.Searching, error = null, rows = emptyList()) }
        viewModelScope.launch {
            settings.setGmailQuery(s.query)
            val creds = withContext(Dispatchers.IO) {
                credentials.address() to credentials.appPassword()
            }
            val (addr, pw) = creds
            if (addr.isNullOrBlank() || pw.isNullOrBlank()) {
                _state.update { it.copy(busy = MailBusy.None, error = "Set up your Gmail account first.") }
                return@launch
            }
            runCatching { gmail.search(addr, pw, s.query) }.fold(
                onSuccess = { result ->
                    _state.update {
                        it.copy(
                            busy = MailBusy.None,
                            rows = result.summaries.map { m -> MailRow(m, selected = true) },
                            totalMatched = result.totalMatched,
                            searched = true,
                            error = null,
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            busy = MailBusy.None,
                            searched = true,
                            rows = emptyList(),
                            totalMatched = 0,
                            error = messageFor(e),
                        )
                    }
                },
            )
        }
    }

    fun toggle(uid: Long) = _state.update { st ->
        st.copy(rows = st.rows.map { if (it.summary.uid == uid) it.copy(selected = !it.selected) else it })
    }

    fun setAll(selected: Boolean) = _state.update { st ->
        st.copy(rows = st.rows.map { it.copy(selected = selected) })
    }

    fun moveSelected() {
        val s = _state.value
        if (!s.idle) return
        val uids = s.selectedUids
        if (uids.isEmpty()) return
        _state.update { it.copy(busy = MailBusy.Moving, error = null) }
        viewModelScope.launch {
            val creds = withContext(Dispatchers.IO) {
                credentials.address() to credentials.appPassword()
            }
            val (addr, pw) = creds
            if (addr.isNullOrBlank() || pw.isNullOrBlank()) {
                _state.update { it.copy(busy = MailBusy.None, error = "Set up your Gmail account first.") }
                return@launch
            }
            val hadMore = s.hasMoreThanShown
            runCatching { gmail.moveToTrash(addr, pw, uids) }.fold(
                onSuccess = { moved ->
                    _state.update {
                        it.copy(
                            busy = MailBusy.None,
                            rows = emptyList(),
                            searched = false,
                            totalMatched = 0,
                            movedCount = moved,
                            error = null,
                        )
                    }
                    // The query still has matches beyond this batch — pull the next one automatically.
                    if (hadMore) search()
                },
                onFailure = { e ->
                    _state.update { it.copy(busy = MailBusy.None, error = messageFor(e)) }
                },
            )
        }
    }

    private fun messageFor(e: Throwable): String = when (e) {
        is GmailError -> e.message ?: "Gmail request failed."
        else -> e.message ?: "Something went wrong talking to Gmail."
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                JunkEmailViewModel(
                    gmail = container.gmailClient,
                    credentials = container.emailCredentialStore,
                    settings = container.settings,
                )
            }
        }
    }
}
