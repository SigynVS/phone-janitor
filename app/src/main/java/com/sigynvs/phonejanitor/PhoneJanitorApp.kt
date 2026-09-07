package com.sigynvs.phonejanitor

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sigynvs.phonejanitor.di.AppContainer
import com.sigynvs.phonejanitor.quarantine.PurgeWorker
import com.sigynvs.phonejanitor.util.Notifications
import java.util.concurrent.TimeUnit

class PhoneJanitorApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        schedulePurge()
    }

    /** Periodic sweep that deletes quarantined files past their 14-day expiry. */
    private fun schedulePurge() {
        val request = PeriodicWorkRequestBuilder<PurgeWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            PurgeWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
