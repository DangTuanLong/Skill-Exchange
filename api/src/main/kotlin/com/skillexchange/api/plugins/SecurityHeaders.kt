package com.skillexchange.api.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call

/**
 * Thêm các HTTP Security Headers tiêu chuẩn cho toàn bộ response của SkillExchange API (TASK-052).
 * - X-Content-Type-Options: nosniff (ngăn trình duyệt tự đoán MIME-type, chống MIME sniffing attack)
 * - X-Frame-Options: DENY (ngăn không cho nhúng trang trong iframe, chống Clickjacking)
 */
fun Application.configureSecurityHeaders() {
    intercept(ApplicationCallPipeline.Plugins) {
        call.response.headers.append("X-Content-Type-Options", "nosniff")
        call.response.headers.append("X-Frame-Options", "DENY")
    }
}
