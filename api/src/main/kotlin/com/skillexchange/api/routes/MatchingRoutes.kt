package com.skillexchange.api.routes

import com.skillexchange.api.models.ApiError
import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.plugins.getUserId
import com.skillexchange.api.services.InMemoryRateLimiter
import com.skillexchange.api.services.matching.MatchingService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

const val MATCHING_RATE_LIMIT_PER_MINUTE = 30

/**
 * In-memory rate limiter per user (30 requests/phút).
 * Giới hạn áp dụng trên từng instance server độc lập (sliding window 1 phút).
 */
val defaultMatchingRateLimiter = InMemoryRateLimiter(
    maxRequests = MATCHING_RATE_LIMIT_PER_MINUTE,
    windowMillis = 60_000L
)

fun Route.matchingRoutes(
    matchingService: MatchingService,
    rateLimiter: InMemoryRateLimiter = defaultMatchingRateLimiter
) {

    route("/api/matching") {
        authenticate("auth-jwt") {
            // GET /api/matching/suggestions/potential?limit=10&cursor=...
            get("/suggestions/potential") {
                val callerUserId = call.getUserId()
                if (!rateLimiter.tryAcquire(callerUserId)) {
                    return@get call.respond(
                        HttpStatusCode.TooManyRequests,
                        ApiError(
                            status = 429,
                            code = ErrorCodes.RATE_LIMITED,
                            message = "Bạn đang gửi yêu cầu gợi ý quá nhanh. Vui lòng thử lại sau 1 phút."
                        )
                    )
                }

                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 10
                val cursor = call.request.queryParameters["cursor"]
                val suggestions = matchingService.getPotentialSuggestions(callerUserId, limit, cursor)
                call.respond(HttpStatusCode.OK, ApiSuccess(data = suggestions))
            }

            // GET /api/matching/suggestions?limit=20&cursor=...
            get("/suggestions") {
                val callerUserId = call.getUserId()
                if (!rateLimiter.tryAcquire(callerUserId)) {
                    return@get call.respond(
                        HttpStatusCode.TooManyRequests,
                        ApiError(
                            status = 429,
                            code = ErrorCodes.RATE_LIMITED,
                            message = "Bạn đang gửi yêu cầu gợi ý quá nhanh. Vui lòng thử lại sau 1 phút."
                        )
                    )
                }

                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 20
                val cursor = call.request.queryParameters["cursor"]
                val suggestions = if (cursor != null) {
                    matchingService.getSuggestions(callerUserId, limit, cursor)
                } else {
                    matchingService.getSuggestions(callerUserId, limit)
                }
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
