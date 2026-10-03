package com.skillexchange.app.domain.model

enum class AppStartDestination(val route: String) {
    ONBOARDING("onboarding"),
    LOGIN("login"),
    PROFILE_SETUP("profile_setup"),
    HOME("home")
}

object StartDestinationDecision {
    /**
     * Quyết định start destination theo FR-AUTH-5:
     * - chưa xem onboarding -> Onboarding ("onboarding")
     * - đã xem onboarding, chưa có token -> Login ("login")
     * - đã xem onboarding, có token, chưa hoàn thành profile -> Profile Setup ("profile_setup")
     * - đã xem onboarding, có token, đã hoàn thành profile -> Home ("home")
     */
    fun decide(
        onboardingSeen: Boolean,
        hasToken: Boolean,
        profileCompleted: Boolean
    ): AppStartDestination {
        return when {
            !onboardingSeen -> AppStartDestination.ONBOARDING
            !hasToken -> AppStartDestination.LOGIN
            !profileCompleted -> AppStartDestination.PROFILE_SETUP
            else -> AppStartDestination.HOME
        }
    }
}
