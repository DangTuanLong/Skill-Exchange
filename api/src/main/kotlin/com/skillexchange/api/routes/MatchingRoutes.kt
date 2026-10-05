package com.skillexchange.api.routes

import com.skillexchange.api.plugins.getUserId
import com.skillexchange.api.services.matching.MatchingService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.matchingRoutes(matchingService: MatchingService) {

    route("/api/matching") {
        authenticate("auth-jwt") {
            // GET /api/matching/suggestions?limit=20
            get("/suggestions") {
                val callerUserId = call.getUserId()
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 20
                val suggestions = matchingService.getSuggestions(callerUserId, limit)
                call.respond(HttpStatusCode.OK, ApiSuccess(data = suggestions))
            }

            // GET /api/matching/score/{userId}
            get("/score/{userId}") {
                val callerUserId = call.getUserId()
                val targetUserId = call.parameters["userId"]

                if (targetUserId.isNullOrBlank() || targetUserId == callerUserId) {
                    return@get call.respond(
                        HttpStatusCode.NotFound,
                        RouteApiError(
                            message = "Không tìm thấy người dùng hoặc không thể tính điểm tương thích",
                            code = 404
                        )
                    )
                }

                val score = matchingService.getScore(callerUserId, targetUserId)
                if (score == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        RouteApiError(
                            message = "Không tìm thấy kỹ năng phù hợp để tính điểm tương thích",
                            code = 404
                        )
                    )
                } else {
                    call.respond(HttpStatusCode.OK, ApiSuccess(data = score))
                }
            }
        }
    }
}
