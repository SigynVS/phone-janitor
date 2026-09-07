package com.sigynvs.phonejanitor.quarantine

import android.content.Context
import android.media.MediaScannerConnection

/** Nudges MediaStore after a raw File move so the gallery drops stale entries and picks up new paths. */
object MediaScanned {
    fun refresh(context: Context, vararg paths: String) {
        val clean = paths.filter { it.isNotBlank() }.toTypedArray()
        if (clean.isEmpty()) return
        runCatching {
            MediaScannerConnection.scanFile(context.applicationContext, clean, null, null)
        }
    }
}
