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

        // GET /api/profile/{userId} - public
        get("/{userId}") {
            val userId = call.parameters["userId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing userId"))
            val profile = profileService.getProfile(userId)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Profile not found"))
            call.respond(mapOf("success" to true, "data" to profile))
        }

        // Yêu cầu JWT cho các routes sau
        authenticate("auth-jwt") {

            // GET /api/profile/me - lấy profile của mình
            get("/me") {
                val userId = call.getUserId()
                val profile = profileService.getProfile(userId)
                    ?: return@get call.respond(HttpStatusCode.NotFound,
                        mapOf("success" to false, "message" to "Profile chưa được tạo"))
                call.respond(mapOf("success" to true, "data" to profile))
            }

            // PUT /api/profile - tạo hoặc cập nhật profile
            put {
                val userId = call.getUserId()
                val req = call.receive<UpdateProfileRequest>()
                if (req.fullName.isBlank()) {
                    return@put call.respond(HttpStatusCode.BadRequest,
                        mapOf("success" to false, "message" to "Tên không được để trống"))
                }
                val profile = profileService.upsertProfile(userId, req)
                call.respond(HttpStatusCode.OK, mapOf("success" to true, "data" to profile))
            }
        }
    }
}
