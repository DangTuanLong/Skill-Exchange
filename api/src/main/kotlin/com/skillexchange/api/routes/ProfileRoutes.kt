package com.skillexchange.api.routes

import com.skillexchange.api.models.profile.UpdateProfileRequest
import com.skillexchange.api.plugins.getUserId
import com.skillexchange.api.services.ProfileService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.profileRoutes(profileService: ProfileService) {
    route("/api/profile") {

        // Yêu cầu JWT cho me và put profile
        authenticate("auth-jwt") {

            // GET /api/profile/me - lấy profile của mình
            get("/me") {
                val userId = call.getUserId()
                val profile = profileService.getProfile(userId)
                    ?: return@get call.respond(HttpStatusCode.NotFound, RouteApiError(message = "Profile chưa được tạo", code = 404))
                call.respond(HttpStatusCode.OK, ApiSuccess(data = profile))
            }

            // PUT /api/profile - tạo hoặc cập nhật profile
            put {
                val userId = call.getUserId()
                val req = call.receive<UpdateProfileRequest>()
                if (req.fullName.isBlank()) {
                    return@put call.respond(HttpStatusCode.BadRequest, RouteApiError(message = "Tên không được để trống", code = 400))
                }
                val profile = profileService.upsertProfile(userId, req)
                call.respond(HttpStatusCode.OK, ApiSuccess(data = profile))
            }
        }

        // GET /api/profile/{userId} - public
        get("/{userId}") {
            val userId = call.parameters["userId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, RouteApiError(message = "Missing userId", code = 400))
            val profile = profileService.getProfile(userId)
                ?: return@get call.respond(HttpStatusCode.NotFound, RouteApiError(message = "Profile not found", code = 404))
            call.respond(HttpStatusCode.OK, ApiSuccess(data = profile))
        }
    }
}
