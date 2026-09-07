package com.sigynvs.phonejanitor.email

import android.content.pm.ServiceInfo
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sigynvs.phonejanitor.PhoneJanitorApp
import com.sigynvs.phonejanitor.R
import com.sigynvs.phonejanitor.util.Notifications
import kotlinx.coroutines.CancellationException

/**
 * Runs the "move every matching message to Trash" drain as a foreground service so an aggressive
 * OEM (Samsung) can't freeze it the moment the app is backgrounded. If the process is still killed,
 * WorkManager restarts this worker and the re-search picks up where it left off.
 */
class BulkMoveWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(0, 0)

    override suspend fun doWork(): Result {
        val app = applicationContext as? PhoneJanitorApp ?: return Result.success()
        val container = app.container
        val mover = container.gmailBulkMover
        val query = inputData.getString(KEY_QUERY)
        if (query.isNullOrBlank()) {
            mover.publishDone(0, 0, "No search to run.")
            return Result.success()
        }

        setForeground(foregroundInfo(0, 0))

        val address = container.emailCredentialStore.address()
        val password = container.emailCredentialStore.appPassword()
        if (address.isNullOrBlank() || password.isNullOrBlank()) {
            mover.publishDone(0, 0, "Set up your Gmail account first.")
            return Result.success()
        }

        return runCatching {
            container.gmailClient.moveAllMatching(address, password, query) { moved, total ->
                mover.publishProgress(moved, total)
                updateNotification(moved, total)
            }
        }.fold(
            onSuccess = { outcome ->
                mover.publishDone(outcome.moved, outcome.total, outcome.error)
                postDone(outcome.moved, outcome.total, outcome.error)
                Result.success()
            },
            onFailure = { e ->
                if (e is CancellationException) {
                    mover.publishDone(mover.lastMoved, mover.lastTotal, "Stopped. Run it again to continue.")
                    postDone(mover.lastMoved, mover.lastTotal, "Stopped — run it again to continue.")
                } else {
                    val message = (e as? GmailError)?.message ?: e.message ?: "Bulk move failed."
                    mover.publishDone(mover.lastMoved, mover.lastTotal, message)
                    postDone(mover.lastMoved, mover.lastTotal, message)
                }
                Result.success()
            },
        )
    }

    private fun postDone(moved: Int, total: Int, error: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
        ) return
        val text = buildString {
            append("Moved $moved")
            if (total > 0) append(" of $total")
            append(" to Gmail Trash.")
            error?.let { append(" $it") }
        }
        val notification = NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_MAINTENANCE)
            .setSmallIcon(R.drawable.ic_stat_clean)
            .setContentTitle("Junk email cleanup finished")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(NOTIF_ID_DONE, notification)
        }
    }

    private fun updateNotification(moved: Int, total: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            NotificationManagerCompat.from(applicationContext).areNotificationsEnabled().not()
        ) return
        runCatching {
            NotificationManagerCompat.from(applicationContext)
                .notify(NOTIF_ID, buildNotification(moved, total))
        }
    }

    private fun foregroundInfo(moved: Int, total: Int): ForegroundInfo {
        val notification = buildNotification(moved, total)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(moved: Int, total: Int): android.app.Notification {
        val stopIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        return NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_MAINTENANCE)
            .setSmallIcon(R.drawable.ic_stat_clean)
            .setContentTitle("Clearing junk email")
            .setContentText(
                if (total > 0) "Moved $moved of $total to Gmail Trash" else "Moved $moved to Gmail Trash",
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Stop", stopIntent)
            .apply {
                if (total > 0) setProgress(total, moved, false) else setProgress(0, 0, true)
            }
            .build()
    }

    companion object {
        const val UNIQUE_NAME = "bulk-email-move"
        const val KEY_QUERY = "query"
        private const val NOTIF_ID = 7301
        private const val NOTIF_ID_DONE = 7302
    }
}
