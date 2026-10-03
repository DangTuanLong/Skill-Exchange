package com.skillexchange.api.plugins

import com.skillexchange.api.routes.authRoutes
import com.skillexchange.api.routes.healthRoutes
import com.skillexchange.api.routes.profileRoutes
import com.skillexchange.api.routes.skillRoutes
import com.skillexchange.api.routes.userRoutes
import com.skillexchange.api.services.AuthService
import com.skillexchange.api.services.ProfileService
import com.skillexchange.api.services.SkillService
import com.skillexchange.api.services.UserService
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    val authService: AuthService by inject()
    val profileService: ProfileService by inject()
    val skillService: SkillService by inject()
    val userService: UserService by inject()

    routing {
        healthRoutes()
        authRoutes(authService)
        profileRoutes(profileService)
        skillRoutes(skillService, userService)
        userRoutes(userService)
    }
}
