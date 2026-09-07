package com.sigynvs.phonejanitor.quarantine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sigynvs.phonejanitor.PhoneJanitorApp
import com.sigynvs.phonejanitor.util.Notifications
import com.sigynvs.phonejanitor.util.formatBytes

/** Runs every ~12h. Deletes quarantined files past their 14-day expiry and reports what it freed. */
class PurgeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? PhoneJanitorApp ?: return Result.success()
        val outcome = runCatching { app.container.quarantineStore.purgeExpired() }
            .getOrElse { return Result.retry() }
        if (outcome.moved > 0) postSummary(outcome)
        return Result.success()
    }

    private fun postSummary(outcome: QuarantineOutcome) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val notification = NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_MAINTENANCE)
            .setSmallIcon(com.sigynvs.phonejanitor.R.drawable.ic_stat_clean)
            .setContentTitle("Quarantine emptied")
            .setContentText(
                "Deleted ${outcome.moved} expired file(s) · freed ${formatBytes(outcome.freedBytes)}"
            )
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(PURGE_NOTIF_ID, notification)
        }
    }

    companion object {
        const val UNIQUE_NAME = "quarantine-purge"
        private const val PURGE_NOTIF_ID = 4201
    }
}
