package com.skillexchange.app.navigation

/**
 * Tất cả các screen routes trong app.
 * Dùng sealed class để type-safe, tránh typo khi navigate.
 */
sealed class Screen(val route: String) {
    // Auth
    data object Onboarding : Screen("onboarding")
    data object Login : Screen("login")
    data object Register : Screen("register")
    data object OtpVerification : Screen("otp_verification/{email}") {
        fun createRoute(email: String) = "otp_verification/$email"
    }

    // Main (sau khi đăng nhập)
    data object Home : Screen("home")
    data object Discovery : Screen("discovery")
    data object Notifications : Screen("notifications")
    data object Profile : Screen("profile")

    // Profile
    data object ProfileSetup : Screen("profile_setup")
    data object SkillSelection : Screen("skill_selection")
    data object ProfileDetail : Screen("profile_detail/{userId}") {
        fun createRoute(userId: String) = "profile_detail/$userId"
    }

    // Booking
    data object BookingRequest : Screen("booking_request/{receiverId}") {
        fun createRoute(receiverId: String) = "booking_request/$receiverId"
    }
    data object BookingDetail : Screen("booking_detail/{exchangeId}") {
        fun createRoute(exchangeId: String) = "booking_detail/$exchangeId"
    }
    data object BookingList : Screen("booking_list")

    // Chat
    data object ChatList : Screen("chat_list")
    data object ChatDetail : Screen("chat_detail/{chatId}") {
        fun createRoute(chatId: String) = "chat_detail/$chatId"
    }

    // Rating
    data object Rating : Screen("rating/{exchangeId}") {
        fun createRoute(exchangeId: String) = "rating/$exchangeId"
    }

    // Dashboard
    data object Dashboard : Screen("dashboard")
}
