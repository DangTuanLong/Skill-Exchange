package com.skillexchange.api.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.uri
import org.slf4j.event.Level

internal val SENSITIVE_QUERY_REGEX = Regex("(?i)([?&](?:token|secret|password|key|refresh_token|api_key|access_token)=)[^&]*")

/**
 * Che giấu giá trị của các tham số nhạy cảm trong query string trước khi log ra console / file log.
 */
fun sanitizeUri(uri: String): String {
    return uri.replace(SENSITIVE_QUERY_REGEX) { matchResult ->
        "${matchResult.groupValues[1]}***"
    }
}

fun Application.configureMonitoring() {
    install(CallLogging) {
        level = Level.INFO
        // Không log health check để tránh spam từ anti-sleep cron
        filter { call ->
            val path = call.request.path()
            path.startsWith("/api") && !path.contains("/health")
        }
        format { call ->
            val status = call.response.status()?.value ?: 0
            val httpMethod = call.request.httpMethod.value
            val sanitized = sanitizeUri(call.request.uri)
            "$httpMethod $sanitized -> $status"
        }
    }
}
