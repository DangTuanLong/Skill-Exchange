package com.skillexchange.app.di

import com.skillexchange.app.core.network.NetworkModule
import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.data.remote.auth.AuthRemoteDataSource
import com.skillexchange.app.data.remote.profile.ProfileRemoteDataSource
import com.skillexchange.app.data.remote.skill.SkillRemoteDataSource
import com.skillexchange.app.data.repository.AuthRepositoryImpl
import com.skillexchange.app.data.repository.ProfileRepositoryImpl
import com.skillexchange.app.data.repository.SkillRepositoryImpl
import com.skillexchange.app.domain.repository.IAuthRepository
import com.skillexchange.app.domain.repository.IProfileRepository
import com.skillexchange.app.domain.repository.ISkillRepository
import com.skillexchange.app.presentation.auth.AuthViewModel
import com.skillexchange.app.presentation.profile.ProfileViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    includes(NetworkModule.module)

    // ── Security ─────────────────────────────────────────────────────
    single { TokenManager(androidContext()) }

    // ── Auth ─────────────────────────────────────────────────────────
    single { AuthRemoteDataSource(get()) }
    single<IAuthRepository> { AuthRepositoryImpl(get(), get()) }
    viewModel { AuthViewModel(get(), get()) }

    // ── Profile & Skills ─────────────────────────────────────────────
    single { ProfileRemoteDataSource(get()) }
    single { SkillRemoteDataSource(get()) }
    single<IProfileRepository> { ProfileRepositoryImpl(get(), get()) }
    single<ISkillRepository>   { SkillRepositoryImpl(get(), get()) }
    viewModel { ProfileViewModel(get(), get()) }
}
