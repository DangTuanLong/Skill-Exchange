package com.skillexchange.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.skillexchange.app.core.network.SessionManager
import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.presentation.auth.LoginScreen
import com.skillexchange.app.presentation.auth.OtpVerificationScreen
import com.skillexchange.app.presentation.auth.RegisterScreen
import com.skillexchange.app.presentation.main.MainScreen
import com.skillexchange.app.presentation.onboarding.OnboardingScreen
import com.skillexchange.app.presentation.profile.ProfileDetailScreen
import com.skillexchange.app.presentation.profile.ProfileSetupScreen
import com.skillexchange.app.presentation.profile.SkillSelectionScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.skillexchange.app.core.notification.InAppNotificationManager
import org.koin.compose.koinInject

/**
 * Root NavHost — với Navigation Guard.
 * startDestination được tính từ MainActivity dựa vào TokenManager.
 */
@Composable
fun SkillExchangeNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Onboarding.route,
    deepLinkRoute: String? = null,
    tokenManager: TokenManager = koinInject()
) {
    val snackbarHostState = remember { SnackbarHostState() }

    // Xử lý deep link mở trực tiếp từ notification
    LaunchedEffect(deepLinkRoute) {
        if (!deepLinkRoute.isNullOrBlank() && tokenManager.isLoggedIn()) {
            navController.navigate(deepLinkRoute)
        }
    }

    // Xử lý thông báo foreground (in-app banner)
    LaunchedEffect(Unit) {
        InAppNotificationManager.notificationFlow.collect { notif ->
            val result = snackbarHostState.showSnackbar(
                message = "${notif.title}: ${notif.body}",
                actionLabel = "Xem",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                if (notif.type == "NEW_REQUEST" && notif.entityId != null && tokenManager.isLoggedIn()) {
                    navController.navigate(Screen.BookingDetail.createRoute(notif.entityId))
                }
            }
        }
    }

    // Quan sát sự kiện session hết hạn do refresh token thất bại.
    // clearSession() đã được gọi trong NetworkModule trước khi emit.
    // Điều hướng về Login và xóa toàn bộ back stack — onboardingSeen vẫn được giữ.
    LaunchedEffect(Unit) {
        SessionManager.sessionExpiredFlow.collect {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {

        // ── Onboarding ───────────────────────────────────────────────
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinish = {
                    tokenManager.saveOnboardingSeen(true)
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
                },
                onNavigateToProfileSetup = {
                    navController.navigate(Screen.ProfileSetup.route) {
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
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToProfileSetup = {
                    navController.navigate(Screen.ProfileSetup.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Main Shell with Bottom Navigation Bar ─────────────────────
        composable(Screen.Home.route) {
            MainScreen(rootNavController = navController)
        }

        // ── Standalone Full Screen Pages ──────────────────────────────
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
                },
                onNavigateToSkillSelection = {
                    navController.navigate(Screen.SkillSelection.route)
                }
            )
        }

        composable(
            route = Screen.BookingRequest.route,
            arguments = listOf(navArgument("receiverId") { type = NavType.StringType })
        ) {
            com.skillexchange.app.presentation.booking.request.BookingRequestScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { exchangeId, receiverId ->
                    navController.navigate(Screen.BookingDetail.createRoute(exchangeId)) {
                        popUpTo(Screen.BookingRequest.createRoute(receiverId)) { inclusive = true }
                    }
                },
                onNavigateToSkillSelection = {
                    navController.navigate(Screen.SkillSelection.route)
                }
            )
        }

        composable(
            route = Screen.BookingDetail.route,
            arguments = listOf(navArgument("exchangeId") { type = NavType.StringType })
        ) {
            com.skillexchange.app.presentation.booking.detail.BookingDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRating = { exchangeId ->
                    navController.navigate(Screen.Rating.createRoute(exchangeId))
                }
            )
        }

        composable(
            route = Screen.ChatDetail.route,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { PlaceholderScreen("Chat Detail") }

        composable(
            route = Screen.Rating.route,
            arguments = listOf(navArgument("exchangeId") { type = NavType.StringType })
        ) { backStackEntry ->
            val exchangeId = backStackEntry.arguments?.getString("exchangeId") ?: ""
            com.skillexchange.app.presentation.rating.RatingScreen(
                exchangeId = exchangeId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Dashboard.route) { PlaceholderScreen("Dashboard") }
    }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}
