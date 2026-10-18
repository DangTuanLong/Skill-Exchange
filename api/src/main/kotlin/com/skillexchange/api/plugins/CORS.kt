package com.skillexchange.api.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

/**
 * Cấu hình CORS theo DEC-015 (TASK-052).
 * Tuyệt đối không dùng anyHost() mở toang mọi domain.
 * Cho phép danh sách nguồn chỉ định qua biến môi trường CORS_ALLOWED_ORIGINS,
 * fallback về localhost cho môi trường phát triển cục bộ.
 */
fun Application.configureCORS() {
    install(CORS) {
        val configuredOrigins = (System.getenv("CORS_ALLOWED_ORIGINS") ?: "").trim()
        val defaultOrigins = listOf("localhost:3000", "localhost:5173", "localhost:8080", "127.0.0.1:3000", "127.0.0.1:5173", "127.0.0.1:8080")

        val origins = if (configuredOrigins.isNotBlank()) {
            configuredOrigins.split(",")
                .map { it.trim().removePrefix("http://").removePrefix("https://").trimEnd('/') }
                .filter { it.isNotEmpty() }
        } else {
            defaultOrigins
        }

        for (host in origins) {
            allowHost(host, schemes = listOf("http", "https"))
        }

        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)
        allowHeader("X-Job-Secret")

        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
    }
}
