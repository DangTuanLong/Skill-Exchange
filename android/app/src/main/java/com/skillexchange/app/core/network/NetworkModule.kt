package com.skillexchange.app.core.network

import android.content.Context
import com.skillexchange.app.core.common.Constants
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

import com.skillexchange.app.core.security.TokenManager
import io.ktor.client.request.header

object NetworkModule {
    val module = module {
        single { provideHttpClient(androidContext(), get()) }
    }
}

/**
 * Ktor HttpClient được cấu hình cho Android engine với Token Interceptor.
 */
fun provideHttpClient(context: Context, tokenManager: TokenManager): HttpClient {
    return HttpClient(Android) {
        engine {
            connectTimeout = Constants.CONNECT_TIMEOUT_MS.toInt()
            socketTimeout = Constants.SOCKET_TIMEOUT_MS.toInt()
        }

        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true   // Không crash khi server thêm field mới
                encodeDefaults = true
            })
        }

        install(Logging) {
            level = LogLevel.BODY
            logger = object : Logger {
                override fun log(message: String) {
                    android.util.Log.d("KtorClient", message)
                }
            }
        }

        defaultRequest {
            contentType(ContentType.Application.Json)
            url(Constants.BASE_URL)
            tokenManager.getAccessToken()?.let { token ->
                header("Authorization", "Bearer $token")
            }
        }
    }
}
