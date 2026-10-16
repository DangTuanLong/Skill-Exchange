package com.skillexchange.api.routes

import com.skillexchange.api.models.stats.LeaderboardResponse
import com.skillexchange.api.models.stats.UserStatsResponse
import com.skillexchange.api.plugins.getUserId
import com.skillexchange.api.services.stats.StatsService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.statsRoutes(statsService: StatsService) {
    route("/api") {
        authenticate("auth-jwt") {
            // GET /api/stats/me
            get("/stats/me") {
                val userId = call.getUserId()
                val stats = statsService.getUserStats(userId)
                call.respond(HttpStatusCode.OK, UserStatsResponse(data = stats))
            }
        }

        // GET /api/leaderboard
        get("/leaderboard") {
            val leaderboard = statsService.getLeaderboard()
            call.respond(HttpStatusCode.OK, LeaderboardResponse(data = leaderboard))
        }
    }
}
