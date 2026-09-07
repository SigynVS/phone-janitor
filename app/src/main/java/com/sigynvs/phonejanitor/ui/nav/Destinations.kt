package com.sigynvs.phonejanitor.ui.nav

import com.sigynvs.phonejanitor.scan.ScanType

sealed class Dest(val route: String) {
    data object Dashboard : Dest("dashboard")
    data object Review : Dest("review/{type}")
    data object JunkEmail : Dest("junk-email")
    data object Quarantine : Dest("quarantine")
    data object Settings : Dest("settings")

    companion object {
        const val ARG_TYPE = "type"
        fun review(type: ScanType): String = "review/${type.name}"
    }
}
