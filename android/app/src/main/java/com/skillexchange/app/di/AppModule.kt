package com.skillexchange.app.di

import androidx.room.Room
import com.skillexchange.app.core.network.NetworkModule
import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.data.local.AppDatabase
import com.skillexchange.app.data.local.ProfileLocalDataSource
import com.skillexchange.app.data.remote.auth.AuthRemoteDataSource
import com.skillexchange.app.data.remote.discovery.DiscoveryRemoteDataSource
import com.skillexchange.app.data.remote.profile.ProfileRemoteDataSource
import com.skillexchange.app.data.remote.skill.SkillRemoteDataSource
import com.skillexchange.app.data.repository.AuthRepositoryImpl
import com.skillexchange.app.data.repository.DiscoveryRepositoryImpl
import com.skillexchange.app.data.repository.ProfileRepositoryImpl
import com.skillexchange.app.data.repository.SkillRepositoryImpl
import com.skillexchange.app.domain.repository.IAuthRepository
import com.skillexchange.app.domain.repository.IDiscoveryRepository
import com.skillexchange.app.domain.repository.IProfileRepository
import com.skillexchange.app.domain.repository.ISkillRepository
import com.skillexchange.app.presentation.auth.AuthViewModel
import com.skillexchange.app.presentation.booking.detail.BookingDetailViewModel
import com.skillexchange.app.presentation.booking.list.BookingListViewModel
import com.skillexchange.app.presentation.booking.request.BookingRequestViewModel
import com.skillexchange.app.presentation.discovery.DiscoveryViewModel
import com.skillexchange.app.presentation.profile.ProfileDetailViewModel
import com.skillexchange.app.presentation.profile.ProfileViewModel
import com.skillexchange.app.presentation.rating.RatingViewModel
import com.skillexchange.app.presentation.chat.list.ChatListViewModel
import com.skillexchange.app.presentation.chat.detail.ChatDetailViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    includes(NetworkModule.module)

    // ── Security & Database ───────────────────────────────────────────
    single { TokenManager(androidContext()) }

    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "skillexchange_db"
        )
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration(true)
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .build()
    }

    single { get<AppDatabase>().profileDao() }
    single { ProfileLocalDataSource(get()) }

    // ── Auth ─────────────────────────────────────────────────────────
    single { AuthRemoteDataSource(get()) }
    single<IAuthRepository> { AuthRepositoryImpl(get(), get()) }
    viewModelOf(::AuthViewModel)

    // ── Profile & Skills ─────────────────────────────────────────────
    single { ProfileRemoteDataSource(get()) }
    single { SkillRemoteDataSource(get()) }
    single<IProfileRepository> { ProfileRepositoryImpl(get(), get(), get()) }
    single<ISkillRepository>   { SkillRepositoryImpl(get(), get()) }
    viewModelOf(::ProfileViewModel)
    viewModelOf(::ProfileDetailViewModel)

    // ── Discovery & Search ────────────────────────────────────────────
    single { DiscoveryRemoteDataSource(get()) }
    single<IDiscoveryRepository> { DiscoveryRepositoryImpl(get()) }
    viewModelOf(::DiscoveryViewModel)

    // ── Exchange / Booking ───────────────────────────────────────────
    single { com.skillexchange.app.data.remote.exchange.ExchangeRemoteDataSource(get()) }
    single<com.skillexchange.app.domain.repository.IExchangeRepository> {
        com.skillexchange.app.data.repository.ExchangeRepositoryImpl(get())
    }
    viewModelOf(::BookingRequestViewModel)
    viewModelOf(::BookingDetailViewModel)
    viewModelOf(::BookingListViewModel)

    // ── Device & FCM Push ─────────────────────────────────────────────
    single { com.skillexchange.app.data.remote.device.DeviceRemoteDataSource(get()) }
    single<com.skillexchange.app.domain.repository.IDeviceRepository> {
        com.skillexchange.app.data.repository.DeviceRepositoryImpl(get())
    }
    single { com.skillexchange.app.core.notification.FcmTokenManager(get(), get()) }

    // ── Rating & Reputation (TASK-033) ────────────────────────────────
    single { com.skillexchange.app.data.remote.rating.RatingRemoteDataSource(get()) }
    single<com.skillexchange.app.domain.repository.IRatingRepository> {
        com.skillexchange.app.data.repository.RatingRepositoryImpl(get())
    }
    viewModelOf(::RatingViewModel)

    // ── Chat & Firebase Auth (TASK-030 & TASK-031) ────────────────────
    single { com.skillexchange.app.data.remote.chat.ChatRemoteDataSource(get()) }
    single<com.skillexchange.app.core.auth.IFirebaseAuthManager> {
        com.skillexchange.app.core.auth.FirebaseAuthManager(
            authRemoteDataSource = get(),
            tokenManager = get()
        )
    }
    single<com.skillexchange.app.domain.repository.IChatRepository> {
        com.skillexchange.app.data.repository.ChatRepositoryImpl(
            firebaseAuthManager = get(),
            tokenManager = get(),
            chatRemoteDataSource = get()
        )
    }
    viewModelOf(::ChatListViewModel)
    viewModelOf(::ChatDetailViewModel)
}
