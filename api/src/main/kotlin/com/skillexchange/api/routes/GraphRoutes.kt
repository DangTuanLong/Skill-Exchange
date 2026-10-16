package com.skillexchange.api.routes

import com.skillexchange.api.models.ApiError
import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.services.graph.GraphRateLimiter
import com.skillexchange.api.services.graph.GraphTimeoutException
import com.skillexchange.api.services.graph.SkillGraphService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.graphRoutes(
    skillGraphService: SkillGraphService,
    rateLimiter: GraphRateLimiter
) {
    route("/api/graph") {
        authenticate("auth-jwt") {
            get("/suggest-path") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.subject

                if (userId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiError(
                            status = 401,
                            code = ErrorCodes.UNAUTHORIZED,
                            message = "Yêu cầu đăng nhập để sử dụng tính năng gợi ý vòng trao đổi."
                        )
                    )
                    return@get
                }

                // 1. Kiểm tra giới hạn tần suất (Rate Limit 20 req/phút/user in-memory)
                if (!rateLimiter.tryAcquire(userId)) {
                    call.respond(
                        HttpStatusCode.TooManyRequests,
                        ApiError(
                            status = 429,
                            code = ErrorCodes.RATE_LIMITED,
                            message = "Bạn đã thực hiện quá nhiều yêu cầu gợi ý đồ thị. Vui lòng thử lại sau một phút."
                        )
                    )
                    return@get
                }

                // 2. Validate tham số query
                val depthParam = call.request.queryParameters["depth"]?.toIntOrNull() ?: 3
                val limitParam = call.request.queryParameters["limit"]?.toIntOrNull() ?: 5

                if (depthParam !in 2..4) {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ApiError(
                            status = 422,
                            code = ErrorCodes.VALIDATION_FAILED,
                            message = "Độ sâu chu trình (depth) phải nằm trong khoảng từ 2 đến 4.",
                            fieldErrors = mapOf("depth" to "Giá trị phải từ 2 đến 4")
                        )
                    )
                    return@get
                }

                if (limitParam !in 1..20) {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ApiError(
                            status = 422,
                            code = ErrorCodes.VALIDATION_FAILED,
                            message = "Số lượng kết quả (limit) phải nằm trong khoảng từ 1 đến 20.",
                            fieldErrors = mapOf("limit" to "Giá trị phải từ 1 đến 20")
                        )
                    )
                    return@get
                }

                // 3. Thực thi tìm kiếm chu trình
                try {
                    val result = skillGraphService.findLoops(
                        callerId = userId,
                        maxDepth = depthParam,
                        limit = limitParam,
                        timeoutMs = 2000L
                    )
                    call.respond(HttpStatusCode.OK, result)
                } catch (e: GraphTimeoutException) {
                    call.respond(
                        HttpStatusCode.GatewayTimeout,
                        ApiError(
                            status = 504,
                            code = ErrorCodes.QUERY_TIMEOUT,
                            message = "Hệ thống đang bận tính toán đồ thị phức tạp, vui lòng thử lại sau."
                        )
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiError(
                            status = 500,
                            code = ErrorCodes.INTERNAL,
                            message = "Đã xảy ra lỗi khi tìm kiếm chu trình trao đổi kỹ năng."
                        )
                    )
                }
            }
        }
    }
}
