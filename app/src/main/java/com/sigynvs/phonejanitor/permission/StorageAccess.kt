package com.sigynvs.phonejanitor.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

/** Wraps the "All files access" (MANAGE_EXTERNAL_STORAGE) check and its Settings deep links. */
object StorageAccess {

    fun isGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }

    /** App-specific "Allow access to manage all files" screen. Preferred. */
    fun appSettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.fromParts("package", context.packageName, null),
        )

    /** The system-wide list of apps with all-files access. Fallback for OEMs missing the app screen. */
    fun listSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
}
