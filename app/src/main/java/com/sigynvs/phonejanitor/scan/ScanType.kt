package com.sigynvs.phonejanitor.scan

/**
 * The cleanup categories shown on the dashboard.
 * [enabled] gates whether the card is tappable — Milestone 1 ships only [DOWNLOADS_SCREENSHOTS].
 */
enum class ScanType(
    val title: String,
    val subtitle: String,
    val enabled: Boolean,
) {
    DOWNLOADS_SCREENSHOTS(
        title = "Downloads & Screenshots",
        subtitle = "Old files in Download and Screenshots folders",
        enabled = true,
    ),
    LARGE_FILES(
        title = "Large Files",
        subtitle = "Biggest files on internal storage",
        enabled = true,
    ),
    DUPLICATES(
        title = "Duplicate Files",
        subtitle = "Byte-identical copies",
        enabled = true,
    ),
    JUNK_EMAIL(
        title = "Junk Email",
        subtitle = "Promotional Gmail, old mail, big attachments",
        enabled = true,
    ),
    APP_CACHE(
        title = "App Cache",
        subtitle = "Per-app cache sizes and shortcuts",
        enabled = false,
    );

    companion object {
        fun fromNameOrNull(name: String?): ScanType? =
            entries.firstOrNull { it.name == name }
    }
}
