package com.sigynvs.phonejanitor.util

import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** "1.4 GB", "820 MB", "44 KB", "6 B" — base-1024, one decimal above KB. */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return if (value >= 100 || unitIndex == 0) "${value.toInt()} ${units[unitIndex]}"
    else String.format("%.1f %s", value, units[unitIndex])
}

/** "today", "3 days ago", "5 months ago", "2 years ago". */
fun relativeAge(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val d = TimeUnit.MILLISECONDS.toDays(abs(now - epochMillis))
    return when {
        d <= 0 -> "today"
        d == 1L -> "yesterday"
        d < 30 -> "$d days ago"
        d < 365 -> "${d / 30} months ago"
        else -> "${d / 365} years ago"
    }
}

/** "in 14 days", "in 1 day", "now". Negative clamps to "now". */
fun relativeFuture(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val d = TimeUnit.MILLISECONDS.toDays(epochMillis - now)
    return when {
        d <= 0 -> "now"
        d == 1L -> "in 1 day"
        else -> "in $d days"
    }
}
