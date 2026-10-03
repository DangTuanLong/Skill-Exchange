package com.skillexchange.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class StartDestinationDecisionTest {

    @Test
    fun `when onboarding is not seen return ONBOARDING regardless of token or profile`() {
        val destination1 = StartDestinationDecision.decide(onboardingSeen = false, hasToken = false, profileCompleted = false)
        assertEquals(AppStartDestination.ONBOARDING, destination1)

        val destination2 = StartDestinationDecision.decide(onboardingSeen = false, hasToken = true, profileCompleted = true)
        assertEquals(AppStartDestination.ONBOARDING, destination2)
    }

    @Test
    fun `when onboarding seen but no token return LOGIN`() {
        val destination1 = StartDestinationDecision.decide(onboardingSeen = true, hasToken = false, profileCompleted = false)
        assertEquals(AppStartDestination.LOGIN, destination1)

        val destination2 = StartDestinationDecision.decide(onboardingSeen = true, hasToken = false, profileCompleted = true)
        assertEquals(AppStartDestination.LOGIN, destination2)
    }

    @Test
    fun `when onboarding seen and has token but profile incomplete return PROFILE_SETUP`() {
        val destination = StartDestinationDecision.decide(onboardingSeen = true, hasToken = true, profileCompleted = false)
        assertEquals(AppStartDestination.PROFILE_SETUP, destination)
    }

    @Test
    fun `when onboarding seen, has token, and profile completed return HOME`() {
        val destination = StartDestinationDecision.decide(onboardingSeen = true, hasToken = true, profileCompleted = true)
        assertEquals(AppStartDestination.HOME, destination)
    }
}
