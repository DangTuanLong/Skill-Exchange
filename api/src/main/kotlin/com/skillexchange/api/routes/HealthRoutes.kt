package com.skillexchange.api.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val timestamp: Long
)

/**
 * Health check endpoint cho:
 * 1. Render liveness check (tránh cold start)
 * 2. GitHub Actions anti-sleep cron job (ping mỗi 14 phút)
 * 3. Frontend kiểm tra server availability
 */
fun Route.healthRoutes() {
    route("/health") {
        get {
            call.respond(
                HttpStatusCode.OK,
                HealthResponse(
                    status = "ok",
                    version = "1.0.0",
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    // Render health check path (Render dùng "/" mặc định)
    get("/") {
        call.respond(HttpStatusCode.OK, mapOf("status" to "SkillExchange API is running"))
    }
}
