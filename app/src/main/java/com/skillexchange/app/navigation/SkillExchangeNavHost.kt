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
import com.skillexchange.app.presentation.discovery.DiscoveryScreen
import com.skillexchange.app.presentation.onboarding.OnboardingScreen
import com.skillexchange.app.presentation.profile.ProfileDetailScreen
import com.skillexchange.app.presentation.profile.ProfileSetupScreen
import com.skillexchange.app.presentation.profile.SkillSelectionScreen

/**
 * Root NavHost — với Navigation Guard.
 * startDestination được tính từ MainActivity dựa vào TokenManager.
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

        // ── Onboarding ───────────────────────────────────────────────
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinish = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Auth ─────────────────────────────────────────────────────
        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                onNavigateToOtp = { email ->
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
                email = email,
                onNavigateToHome = {
                    navController.navigate(Screen.ProfileSetup.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Profile Setup Flow ────────────────────────────────────────
        composable(Screen.ProfileSetup.route) {
            ProfileSetupScreen(
                onNavigateToSkillSelection = {
                    navController.navigate(Screen.SkillSelection.route)
                },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.ProfileSetup.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.SkillSelection.route) {
            SkillSelectionScreen(
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.ProfileSetup.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Discovery & Search ────────────────────────────────────────
        composable(Screen.Discovery.route) {
            DiscoveryScreen(
                onNavigateToProfileDetail = { userId ->
                    navController.navigate(Screen.ProfileDetail.createRoute(userId))
                }
            )
        }

        composable(
            route = Screen.ProfileDetail.route,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            ProfileDetailScreen(
                userId = userId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToBooking = { receiverId ->
                    navController.navigate(Screen.BookingRequest.createRoute(receiverId))
                }
            )
        }

        // ── Main Screens ─────────────────────────────────────────────
        composable(Screen.Home.route)         { PlaceholderScreen("Home") }
        composable(Screen.ChatList.route)    { PlaceholderScreen("Chat") }
        composable(
            route = Screen.ChatDetail.route,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { PlaceholderScreen("Chat Detail") }
        composable(Screen.BookingList.route) { PlaceholderScreen("Booking") }
        composable(
            route = Screen.Rating.route,
            arguments = listOf(navArgument("exchangeId") { type = NavType.StringType })
        ) { PlaceholderScreen("Rating") }
        composable(Screen.Dashboard.route)   { PlaceholderScreen("Dashboard") }
    }
}
