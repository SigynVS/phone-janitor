package com.sigynvs.phonejanitor.email

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * App-scoped controller for "move every message matching this query into Trash". Lives above the
 * screen so leaving Junk Email doesn't kill a 10-minute drain of an 80k-message backlog.
 */
class GmailBulkMover(
    private val gmail: GmailImapClient,
    private val credentials: EmailCredentialStore,
    private val scope: CoroutineScope,
) {
    sealed interface State {
        data object Idle : State
        data class Running(val moved: Int, val total: Int) : State
        data class Done(val moved: Int, val total: Int, val error: String?) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null

    val isRunning: Boolean get() = _state.value is State.Running

    fun start(rawGmailQuery: String, knownTotal: Int) {
        if (isRunning) return
        _state.value = State.Running(0, knownTotal.coerceAtLeast(0))
        job = scope.launch {
            val address = credentials.address()
            val password = credentials.appPassword()
            if (address.isNullOrBlank() || password.isNullOrBlank()) {
                _state.value = State.Done(0, 0, "Set up your Gmail account first.")
                return@launch
            }
            val outcome = runCatching {
                gmail.moveAllMatching(address, password, rawGmailQuery) { moved, total ->
                    _state.value = State.Running(moved, total)
                }
            }
            _state.value = outcome.fold(
                onSuccess = { State.Done(it.moved, it.total, it.error) },
                onFailure = { e ->
                    if (e is kotlinx.coroutines.CancellationException) {
                        val r = _state.value as? State.Running
                        State.Done(r?.moved ?: 0, r?.total ?: 0, "Stopped. Run it again to continue.")
                    } else {
                        State.Done(0, 0, (e as? GmailError)?.message ?: e.message ?: "Bulk move failed.")
                    }
                },
            )
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun acknowledge() {
        if (_state.value is State.Done) _state.value = State.Idle
    }
}
