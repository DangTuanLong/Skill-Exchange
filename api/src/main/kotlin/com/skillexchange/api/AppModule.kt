package com.skillexchange.api

import com.skillexchange.api.services.AuthService
import com.skillexchange.api.services.IAvatarStorageService
import com.skillexchange.api.services.InMemoryRateLimiter
import com.skillexchange.api.services.ProfileService
import com.skillexchange.api.services.R2AvatarStorageService
import com.skillexchange.api.services.SkillService
import com.skillexchange.api.services.UserService
import org.koin.dsl.module

val appModule = module {
    single { AuthService() }
    single { ProfileService() }
    single { SkillService() }
    single { UserService() }
    single<IAvatarStorageService> { R2AvatarStorageService() }
    single { InMemoryRateLimiter(maxRequests = 5, windowMillis = 60_000L) }
}

