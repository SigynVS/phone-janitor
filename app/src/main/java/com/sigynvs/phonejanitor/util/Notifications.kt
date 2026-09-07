package com.sigynvs.phonejanitor.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService

object Notifications {

    const val CHANNEL_MAINTENANCE = "maintenance"

    fun createChannels(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        val maintenance = NotificationChannel(
            CHANNEL_MAINTENANCE,
            "Maintenance",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Quarantine cleanup and scan summaries"
        }
        manager.createNotificationChannel(maintenance)
    }
}
