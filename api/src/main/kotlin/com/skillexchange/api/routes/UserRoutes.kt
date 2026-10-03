package com.skillexchange.api.routes

import com.skillexchange.api.services.UserService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.userRoutes(userService: UserService) {

    // Canonical endpoint theo plan
    route("/api/users") {
        get("/search") {
            val query        = call.request.queryParameters["q"]
            val categoryId   = call.request.queryParameters["category"]?.toIntOrNull()
            val city         = call.request.queryParameters["city"]
            val minLevel     = call.request.queryParameters["minProficiency"]?.toIntOrNull()
                ?: call.request.queryParameters["minLevel"]?.toIntOrNull()
            val type         = call.request.queryParameters["type"]
            val limit        = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 20
            val offset       = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

            val results = userService.searchUsers(query, categoryId, city, minLevel, type, limit, offset)
            call.respond(HttpStatusCode.OK, ApiSuccess(data = results))
        }
    }
}
