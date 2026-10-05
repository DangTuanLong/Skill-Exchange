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
import com.skillexchange.app.presentation.discovery.DiscoveryViewModel
import com.skillexchange.app.presentation.profile.ProfileDetailViewModel
import com.skillexchange.app.presentation.profile.ProfileViewModel
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
            .fallbackToDestructiveMigration(true)
            .fallbackToDestructiveMigrationOnDowngrade()
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
}
