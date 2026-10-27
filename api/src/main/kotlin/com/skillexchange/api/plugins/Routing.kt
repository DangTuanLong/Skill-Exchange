package com.skillexchange.api.plugins

import com.skillexchange.api.routes.authRoutes
import com.skillexchange.api.routes.callRoutes
import com.skillexchange.api.routes.chatRoutes
import com.skillexchange.api.routes.exchangeRoutes
import com.skillexchange.api.routes.healthRoutes
import com.skillexchange.api.routes.matchingRoutes
import com.skillexchange.api.routes.profileRoutes
import com.skillexchange.api.routes.skillRoutes
import com.skillexchange.api.routes.userRoutes
import com.skillexchange.api.services.AuthService
import com.skillexchange.api.services.ProfileService
import com.skillexchange.api.services.SkillService
import com.skillexchange.api.services.UserService
import com.skillexchange.api.services.exchange.ExchangeService
import com.skillexchange.api.services.matching.MatchingService
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject

import com.skillexchange.api.routes.deviceRoutes
import com.skillexchange.api.routes.graphRoutes
import com.skillexchange.api.routes.nlpRoutes
import com.skillexchange.api.routes.notificationRoutes
import com.skillexchange.api.routes.ratingRoutes
import com.skillexchange.api.routes.statsRoutes
import com.skillexchange.api.services.device.DeviceService
import com.skillexchange.api.services.notification.NotificationService
import com.skillexchange.api.services.notification.ReminderSchedulerService
import com.skillexchange.api.services.rating.RatingService
import com.skillexchange.api.services.stats.StatsService

fun Application.configureRouting() {
    val authService: AuthService by inject()
    val firebaseTokenService: com.skillexchange.api.services.auth.IFirebaseTokenService by inject()
    val profileService: ProfileService by inject()
    val skillService: SkillService by inject()
    val userService: UserService by inject()
    val matchingService: MatchingService by inject()
    val exchangeService: ExchangeService by inject()
    val deviceService: DeviceService by inject()
    val ratingService: RatingService by inject()
    val avatarStorageService: com.skillexchange.api.services.IAvatarStorageService by inject()
    val chatAttachmentStorageService: com.skillexchange.api.services.chat.IChatAttachmentStorageService by inject()
    val rateLimiter: com.skillexchange.api.services.InMemoryRateLimiter by inject()
    val notificationService: NotificationService by inject()
    val reminderSchedulerService: ReminderSchedulerService by inject()
    val skillExtractionService: com.skillexchange.api.services.nlp.SkillExtractionService by inject()
    val nlpRateLimiter: com.skillexchange.api.services.nlp.NlpRateLimiter by inject()
    val documentRateLimiter: com.skillexchange.api.services.nlp.DocumentRateLimiter by inject()
    val skillGraphService: com.skillexchange.api.services.graph.SkillGraphService by inject()
    val graphRateLimiter: com.skillexchange.api.services.graph.GraphRateLimiter by inject()
    val statsService: StatsService by inject()
    val authRateLimiter: com.skillexchange.api.services.auth.AuthRateLimiter by inject()
    val reminderRateLimiter: com.skillexchange.api.services.notification.ReminderRateLimiter by inject()
    val callService: com.skillexchange.api.services.call.CallService by inject()

    routing {
        healthRoutes()
        authRoutes(authService, firebaseTokenService, authRateLimiter)
        profileRoutes(profileService, avatarStorageService, rateLimiter)
        skillRoutes(skillService)
        userRoutes(userService)
        matchingRoutes(matchingService)
        exchangeRoutes(exchangeService)
        deviceRoutes(deviceService)
        ratingRoutes(ratingService)
        chatRoutes(chatAttachmentStorageService, exchangeService)
        notificationRoutes(notificationService, reminderSchedulerService, reminderRateLimiter)
        nlpRoutes(skillExtractionService, nlpRateLimiter, documentRateLimiter)
        graphRoutes(skillGraphService, graphRateLimiter)
        statsRoutes(statsService)
        callRoutes(callService)
    }
}
