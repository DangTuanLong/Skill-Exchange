package com.skillexchange.app

import android.app.Application
import com.skillexchange.app.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class SkillExchangeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.DEBUG)
            androidContext(this@SkillExchangeApp)
            modules(appModule)
        }
    }
}
