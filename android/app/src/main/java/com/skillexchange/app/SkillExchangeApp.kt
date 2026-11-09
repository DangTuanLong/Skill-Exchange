package com.skillexchange.app

import android.app.Application
import android.os.StrictMode
import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.di.appModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class SkillExchangeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .penaltyLog()
                    .build()
            )
        }
        val koinApp = startKoin {
            if (BuildConfig.DEBUG) {
                androidLogger(Level.DEBUG)
            }
            androidContext(this@SkillExchangeApp)
            modules(appModule)
        }
        koinApp.koin.get<CoroutineScope>().launch(Dispatchers.IO) {
            koinApp.koin.get<TokenManager>().warmUp()
        }
        registerActivityLifecycleCallbacks(com.skillexchange.app.core.notification.AppLifecycleTracker)
    }
}
