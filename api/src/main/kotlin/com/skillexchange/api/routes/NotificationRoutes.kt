package com.skillexchange.api.routes

import com.skillexchange.api.models.ApiError
import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.notification.MarkReadResponseDto
import com.skillexchange.api.plugins.getUserId
import com.skillexchange.api.services.notification.NotificationService
import com.skillexchange.api.services.notification.ReminderRateLimiter
import com.skillexchange.api.services.notification.ReminderSchedulerService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory
import java.security.MessageDigest

private val logger = LoggerFactory.getLogger("NotificationRoutes")

fun Route.notificationRoutes(
    notificationService: NotificationService,
    reminderSchedulerService: ReminderSchedulerService,
    reminderRateLimiter: ReminderRateLimiter = ReminderRateLimiter()
) {
    route("/api/notifications") {
        authenticate("auth-jwt") {
            // GET /api/notifications - Lấy danh sách thông báo và số lượng chưa đọc
            get {
                val callerUserId = call.getUserId()
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 30

                if (page < 1 || limit < 1 || limit > 100) {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(
                            status = HttpStatusCode.BadRequest.value,
                            code = ErrorCodes.BAD_REQUEST,
                            message = "Tham số phân trang không hợp lệ (page >= 1, 1 <= limit <= 100)"
                        )
                    )
                }

                val response = notificationService.getNotifications(
                    userId = callerUserId,
                    page = page,
                    limit = limit
                )
                call.respond(HttpStatusCode.OK, response)
            }

            // PUT /api/notifications/read-all - Đánh dấu tất cả thông báo của user là đã đọc
            put("/read-all") {
                val callerUserId = call.getUserId()
                val updatedCount = notificationService.markAllAsRead(callerUserId)
                call.respond(
                    HttpStatusCode.OK,
                    MarkReadResponseDto(
                        success = true,
                        message = "Đã đánh dấu tất cả thông báo là đã đọc ($updatedCount thông báo)"
                    )
                )
            }

            // PUT /api/notifications/{id}/read - Đánh dấu một thông báo là đã đọc
            put("/{id}/read") {
                val callerUserId = call.getUserId()
                val notificationId = call.parameters["id"]
                if (notificationId.isNullOrBlank()) {
                    return@put call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(
                            status = HttpStatusCode.BadRequest.value,
                            code = ErrorCodes.BAD_REQUEST,
                            message = "Thiếu id thông báo"
                        )
                    )
                }

                val updated = notificationService.markAsRead(callerUserId, notificationId)
                if (!updated) {
                    return@put call.respond(
                        HttpStatusCode.NotFound,
                        ApiError(
                            status = HttpStatusCode.NotFound.value,
                            code = ErrorCodes.NOT_FOUND,
                            message = "Không tìm thấy thông báo hoặc bạn không có quyền"
                        )
                    )
                }

                call.respond(
                    HttpStatusCode.OK,
                    MarkReadResponseDto(
                        success = true,
                        message = "Đã đánh dấu thông báo là đã đọc"
                    )
                )
            }
        }
    }

    // Endpoint nội bộ cho Job quét nhắc nhở (GitHub Actions / Scheduled Job)
    route("/api/internal/jobs/process-reminders") {
        post {
            if (!reminderRateLimiter.tryAcquire()) {
                return@post call.respond(
                    HttpStatusCode.TooManyRequests,
                    ApiError(
                        status = 429,
                        code = ErrorCodes.RATE_LIMITED,
                        message = "Tần suất gọi job quá nhanh. Vui lòng thử lại sau ít phút."
                    )
                )
            }

            val configuredSecret = (System.getProperty("JOB_SECRET") ?: System.getenv("JOB_SECRET"))?.trim()
            val providedSecret = call.request.headers["X-Job-Secret"]?.trim()

            // So sánh hằng thời gian (constant-time) ngăn chặn Timing Attack, không log secret
            val isAuthorized = !configuredSecret.isNullOrEmpty() &&
                !providedSecret.isNullOrEmpty() &&
                MessageDigest.isEqual(
                    configuredSecret.toByteArray(Charsets.UTF_8),
                    providedSecret.toByteArray(Charsets.UTF_8)
                )

            if (!isAuthorized) {
                // Trả lỗi 401 chung, không tiết lộ chi tiết
                return@post call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiError(
                        status = HttpStatusCode.Unauthorized.value,
                        code = ErrorCodes.UNAUTHORIZED,
                        message = "Unauthorized"
                    )
                )
            }

            val result = reminderSchedulerService.processReminders()
            call.respond(HttpStatusCode.OK, result)
        }
    }
}

