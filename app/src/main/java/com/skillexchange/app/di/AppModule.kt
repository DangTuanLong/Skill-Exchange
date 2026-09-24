package com.skillexchange.app.di

import com.skillexchange.app.core.network.NetworkModule
import com.skillexchange.app.data.remote.auth.AuthRemoteDataSource
import com.skillexchange.app.data.repository.AuthRepositoryImpl
import com.skillexchange.app.domain.repository.IAuthRepository
import com.skillexchange.app.presentation.auth.AuthViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    includes(NetworkModule.module)

    // ── Auth ─────────────────────────────────────────────────────────
    single { AuthRemoteDataSource(get()) }
    single<IAuthRepository> { AuthRepositoryImpl(get()) }
    viewModel { AuthViewModel(get()) }
}
