package com.skillexchange.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

/**
 * Root NavHost của toàn bộ app.
 * Điểm khởi đầu: Onboarding (sẽ được thay bằng logic kiểm tra auth sau).
 */
@Composable
fun SkillExchangeNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Onboarding.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // ── Auth ──────────────────────────────────────────────────
        composable(Screen.Onboarding.route) {
            // TODO: OnboardingScreen(navController) — Tuần 3
            PlaceholderScreen("Onboarding")
        }
        composable(Screen.Login.route) {
            // TODO: LoginScreen(navController) — Tuần 3
            PlaceholderScreen("Login")
        }
        composable(Screen.Register.route) {
            // TODO: RegisterScreen(navController) — Tuần 3
            PlaceholderScreen("Register")
        }
        composable(
            route = Screen.OtpVerification.route,
            arguments = listOf(navArgument("email") { type = NavType.StringType })
        ) { backStackEntry ->
            val email = backStackEntry.arguments?.getString("email") ?: ""
            // TODO: OtpVerificationScreen(email, navController) — Tuần 3
            PlaceholderScreen("OTP Verification")
        }

        // ── Main ──────────────────────────────────────────────────
        composable(Screen.Home.route) {
            PlaceholderScreen("Home")
        }
        composable(Screen.ProfileSetup.route) {
            // TODO: ProfileSetupScreen(navController) — Tuần 4
            PlaceholderScreen("Profile Setup")
        }
        composable(
            route = Screen.ProfileDetail.route,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) {
            PlaceholderScreen("Profile Detail")
        }
        composable(Screen.Discovery.route) {
            PlaceholderScreen("Discovery")
        }
        composable(Screen.ChatList.route) {
            PlaceholderScreen("Chat List")
        }
        composable(
            route = Screen.ChatDetail.route,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) {
            PlaceholderScreen("Chat Detail")
        }
        composable(Screen.BookingList.route) {
            PlaceholderScreen("Booking List")
        }
        composable(
            route = Screen.Rating.route,
            arguments = listOf(navArgument("exchangeId") { type = NavType.StringType })
        ) {
            PlaceholderScreen("Rating")
        }
        composable(Screen.Dashboard.route) {
            PlaceholderScreen("Dashboard")
        }
    }
}
