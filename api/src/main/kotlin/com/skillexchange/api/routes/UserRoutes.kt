package com.skillexchange.api.routes

import com.skillexchange.api.plugins.getUserIdOrNull
import com.skillexchange.api.services.UserService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.userRoutes(userService: UserService) {

    // Canonical endpoint theo plan: GET /api/users/search (TASK-007)
    route("/api/users") {
        authenticate("auth-jwt", optional = true) {
            get("/search") {
                val query        = call.request.queryParameters["q"]
                val categoryIds  = buildList {
                    call.request.queryParameters.getAll("categoryIds")?.forEach { param ->
                        param.split(",").mapNotNull { it.trim().toIntOrNull() }.forEach { add(it) }
                    }
                    call.request.queryParameters["category"]?.toIntOrNull()?.let { add(it) }
                }.distinct()
                val city         = call.request.queryParameters["city"]
                val minLevel     = call.request.queryParameters["minProficiency"]?.toIntOrNull()
                    ?: call.request.queryParameters["minLevel"]?.toIntOrNull()
                val maxLevel     = call.request.queryParameters["maxProficiency"]?.toIntOrNull()
                    ?: call.request.queryParameters["maxLevel"]?.toIntOrNull()
                val type         = call.request.queryParameters["type"]
                val limit        = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 20
                val lastId       = call.request.queryParameters["lastId"]
                val offset       = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                val callerUserId = call.getUserIdOrNull()

                val result = userService.searchUsersWithCursor(
                    query = query,
                    categoryIds = categoryIds,
                    city = city,
                    minProficiency = minLevel,
                    maxProficiency = maxLevel,
                    type = type,
                    limit = limit,
                    lastId = lastId,
                    offset = offset,
                    excludeUserId = callerUserId
                )
                call.respond(HttpStatusCode.OK, ApiSuccess(data = result))
            }
        }
    }
}
