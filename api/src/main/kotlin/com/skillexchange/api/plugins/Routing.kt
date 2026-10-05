package com.skillexchange.api.plugins

import com.skillexchange.api.routes.authRoutes
import com.skillexchange.api.routes.healthRoutes
import com.skillexchange.api.routes.matchingRoutes
import com.skillexchange.api.routes.profileRoutes
import com.skillexchange.api.routes.skillRoutes
import com.skillexchange.api.routes.userRoutes
import com.skillexchange.api.services.AuthService
import com.skillexchange.api.services.ProfileService
import com.skillexchange.api.services.SkillService
import com.skillexchange.api.services.UserService
import com.skillexchange.api.services.matching.MatchingService
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    val authService: AuthService by inject()
    val profileService: ProfileService by inject()
    val skillService: SkillService by inject()
    val userService: UserService by inject()
    val matchingService: MatchingService by inject()
    val avatarStorageService: com.skillexchange.api.services.IAvatarStorageService by inject()
    val rateLimiter: com.skillexchange.api.services.InMemoryRateLimiter by inject()

    routing {
        healthRoutes()
        authRoutes(authService)
        profileRoutes(profileService, avatarStorageService, rateLimiter)
        skillRoutes(skillService, userService)
        userRoutes(userService)
        matchingRoutes(matchingService)
    }
}
