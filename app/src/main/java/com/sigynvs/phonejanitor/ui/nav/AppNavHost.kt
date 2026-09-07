package com.sigynvs.phonejanitor.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sigynvs.phonejanitor.scan.ScanType
import com.sigynvs.phonejanitor.ui.dashboard.DashboardScreen
import com.sigynvs.phonejanitor.ui.email.JunkEmailScreen
import com.sigynvs.phonejanitor.ui.quarantine.QuarantineScreen
import com.sigynvs.phonejanitor.ui.review.ReviewScreen
import com.sigynvs.phonejanitor.ui.settings.SettingsScreen

@Composable
fun AppNavHost() {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Dest.Dashboard.route) {

        composable(Dest.Dashboard.route) {
            DashboardScreen(
                onOpenReview = { type -> nav.navigate(Dest.review(type)) },
                onOpenJunkEmail = { nav.navigate(Dest.JunkEmail.route) },
                onOpenQuarantine = { nav.navigate(Dest.Quarantine.route) },
                onOpenSettings = { nav.navigate(Dest.Settings.route) },
            )
        }

        composable(
            route = Dest.Review.route,
            arguments = listOf(navArgument(Dest.ARG_TYPE) { type = NavType.StringType }),
        ) { backStackEntry ->
            val type = ScanType.fromNameOrNull(backStackEntry.arguments?.getString(Dest.ARG_TYPE))
                ?: ScanType.DOWNLOADS_SCREENSHOTS
            ReviewScreen(
                scanType = type,
                onBack = { nav.popBackStack() },
                onOpenSettings = { nav.navigate(Dest.Settings.route) },
            )
        }

        composable(Dest.JunkEmail.route) {
            JunkEmailScreen(onBack = { nav.popBackStack() })
        }

        composable(Dest.Quarantine.route) {
            QuarantineScreen(onBack = { nav.popBackStack() })
        }

        composable(Dest.Settings.route) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
