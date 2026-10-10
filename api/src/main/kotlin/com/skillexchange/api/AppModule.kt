package com.skillexchange.api

import com.skillexchange.api.services.AuthService
import com.skillexchange.api.services.IAvatarStorageService
import com.skillexchange.api.services.InMemoryRateLimiter
import com.skillexchange.api.services.ProfileService
import com.skillexchange.api.services.R2AvatarStorageService
import com.skillexchange.api.services.SkillService
import com.skillexchange.api.services.UserService
import com.skillexchange.api.services.exchange.ExchangeService
import com.skillexchange.api.services.exchange.IExchangeNotificationHook
import com.skillexchange.api.services.exchange.NoOpExchangeNotificationHook
import com.skillexchange.api.services.matching.MatchingService
import com.skillexchange.api.services.device.DeviceService
import com.skillexchange.api.services.exchange.FcmExchangeNotificationHook
import com.skillexchange.api.services.notification.FcmService
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val appModule = module {
    single { AuthService() }
    single { ProfileService() }
    single { SkillService() }
    single { UserService() }
    single { MatchingService() }
    single { DeviceService() }
    single {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }
    single<com.skillexchange.api.services.notification.IFcmCredentialsProvider> {
        com.skillexchange.api.services.notification.DefaultFcmCredentialsProvider()
    }
    single { FcmService(httpClient = get(), deviceService = get(), credentialsProvider = get()) }
    single<com.skillexchange.api.services.auth.IFirebaseTokenService> {
        com.skillexchange.api.services.auth.FirebaseTokenService()
    }
    single<com.skillexchange.api.services.chat.IFirestoreChatService> {
        com.skillexchange.api.services.chat.FirestoreChatService(httpClient = get(), credentialsProvider = get())
    }
    single<IExchangeNotificationHook> {
        FcmExchangeNotificationHook(
            fcmService = get(),
            deviceService = get(),
            firestoreChatService = get()
        )
    }
    single { ExchangeService(get()) }
    single<IAvatarStorageService> { R2AvatarStorageService() }
    single<com.skillexchange.api.services.chat.IChatAttachmentStorageService> {
        com.skillexchange.api.services.chat.R2ChatAttachmentStorageService()
    }
    single { InMemoryRateLimiter(maxRequests = 5, windowMillis = 60_000L) }
    single { com.skillexchange.api.services.rating.RatingService() }
}

