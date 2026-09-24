package com.skillexchange.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.skillexchange.app.presentation.auth.LoginScreen
import com.skillexchange.app.presentation.auth.OtpVerificationScreen
import com.skillexchange.app.presentation.auth.RegisterScreen

/**
 * Root NavHost của toàn bộ app.
 */
@Composable
fun SkillExchangeNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Login.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // ── Auth ─────────────────────────────────────────────────────
        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                onNavigateToHome     = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                onNavigateToOtp   = { email ->
                    navController.navigate(Screen.OtpVerification.createRoute(email))
                },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.OtpVerification.route,
            arguments = listOf(navArgument("email") { type = NavType.StringType })
        ) { backStackEntry ->
            val email = backStackEntry.arguments?.getString("email") ?: ""
            OtpVerificationScreen(
                email            = email,
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateBack   = { navController.popBackStack() }
            )
        }

        // ── Onboarding (reserved) ─────────────────────────────────────
        composable(Screen.Onboarding.route) {
            PlaceholderScreen("Onboarding")
        }

        // ── Main ─────────────────────────────────────────────────────
        composable(Screen.Home.route) {
            PlaceholderScreen("Home — Tuần 5")
        }
        composable(Screen.ProfileSetup.route) {
            PlaceholderScreen("Profile Setup — Tuần 4")
        }
        composable(
            route = Screen.ProfileDetail.route,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) {
            PlaceholderScreen("Profile Detail — Tuần 4")
        }
        composable(Screen.Discovery.route)   { PlaceholderScreen("Discovery — Tuần 5") }
        composable(Screen.ChatList.route)    { PlaceholderScreen("Chat — Tuần 9") }
        composable(
            route = Screen.ChatDetail.route,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { PlaceholderScreen("Chat Detail — Tuần 9") }
        composable(Screen.BookingList.route) { PlaceholderScreen("Booking — Tuần 7") }
        composable(
            route = Screen.Rating.route,
            arguments = listOf(navArgument("exchangeId") { type = NavType.StringType })
        ) { PlaceholderScreen("Rating — Tuần 8") }
        composable(Screen.Dashboard.route)   { PlaceholderScreen("Dashboard — Tuần 6") }
    }
}
