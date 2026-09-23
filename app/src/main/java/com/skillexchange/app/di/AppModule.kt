package com.skillexchange.app.di

import com.skillexchange.app.core.network.NetworkModule
import org.koin.dsl.module

/**
 * Root Koin module — tập hợp tất cả module con.
 * Mỗi feature sẽ có module riêng được thêm vào đây.
 */
val appModule = module {
    includes(
        NetworkModule.module,
    )
}
