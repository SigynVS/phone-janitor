package com.sigynvs.phonejanitor.email

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Starts / stops the "drain a whole Gmail search into Trash" job (a foreground-service
 * [BulkMoveWorker]) and holds the observable progress. The worker writes progress back here via
 * [publishProgress] / [publishDone].
 */
class GmailBulkMover(context: Context) {

    private val appContext = context.applicationContext

    sealed interface State {
        data object Idle : State
        data class Running(val moved: Int, val total: Int) : State
        data class Done(val moved: Int, val total: Int, val error: String?) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    val isRunning: Boolean get() = _state.value is State.Running

    val lastMoved: Int
        get() = when (val s = _state.value) {
            is State.Running -> s.moved
            is State.Done -> s.moved
            else -> 0
        }

    val lastTotal: Int
        get() = when (val s = _state.value) {
            is State.Running -> s.total
            is State.Done -> s.total
            else -> 0
        }

    fun start(rawGmailQuery: String, knownTotal: Int) {
        if (isRunning) return
        _state.value = State.Running(0, knownTotal.coerceAtLeast(0))
        val request = OneTimeWorkRequestBuilder<BulkMoveWorker>()
            .setInputData(workDataOf(BulkMoveWorker.KEY_QUERY to rawGmailQuery))
            .build()
        WorkManager.getInstance(appContext)
            .enqueueUniqueWork(BulkMoveWorker.UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel() {
        WorkManager.getInstance(appContext).cancelUniqueWork(BulkMoveWorker.UNIQUE_NAME)
    }

    fun acknowledge() {
        if (_state.value is State.Done) _state.value = State.Idle
    }

    // --- called by BulkMoveWorker ---

    fun publishProgress(moved: Int, total: Int) {
        _state.value = State.Running(moved, total)
    }

    fun publishDone(moved: Int, total: Int, error: String?) {
        _state.value = State.Done(moved, total, error)
    }
}
