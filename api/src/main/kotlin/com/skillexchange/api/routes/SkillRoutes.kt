package com.skillexchange.api.routes

import com.skillexchange.api.models.skill.AddUserSkillRequest
import com.skillexchange.api.plugins.getUserId
import com.skillexchange.api.services.SkillService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.skillRoutes(skillService: SkillService) {
    route("/api/skills") {

        // GET /api/skills/categories - public
        get("/categories") {
            val categories = skillService.getCategories()
            call.respond(HttpStatusCode.OK, ApiSuccess(data = categories))
        }

        // GET /api/skills?category=1
        get {
            val categoryId = call.request.queryParameters["category"]?.toIntOrNull()
            val skills = if (categoryId != null) skillService.getSkillsByCategory(categoryId)
                         else emptyList()
            call.respond(HttpStatusCode.OK, ApiSuccess(data = skills))
        }

        authenticate("auth-jwt") {

            // GET /api/skills/user - my skills
            get("/user") {
                val userId = call.getUserId()
                val skills = skillService.getUserSkills(userId)
                call.respond(HttpStatusCode.OK, ApiSuccess(data = skills))
            }

            // GET /api/skills/user/{userId} - other user's skills
            get("/user/{userId}") {
                val userId = call.parameters["userId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, RouteApiError(message = "Missing userId", code = 400))
                val skills = skillService.getUserSkills(userId)
                call.respond(HttpStatusCode.OK, ApiSuccess(data = skills))
            }

            // POST /api/skills/user - add skill
            post("/user") {
                val userId = call.getUserId()
                val req = call.receive<AddUserSkillRequest>()
                if (req.type !in listOf("HAVE", "WANT")) {
                    return@post call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        RouteApiError(message = "type phải là HAVE hoặc WANT", code = 422)
                    )
                }
                if (req.proficiencyLevel !in 1..5) {
                    return@post call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        RouteApiError(message = "Trình độ kỹ năng phải từ 1 đến 5", code = 422)
                    )
                }
                val skill = skillService.addUserSkill(userId, req)
                call.respond(HttpStatusCode.Created, ApiSuccess(data = skill))
            }

            // DELETE /api/skills/user/{id}
            delete("/user/{id}") {
                val userId = call.getUserId()
                val skillId = call.parameters["id"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, RouteApiError(message = "Missing id", code = 400))
                val deleted = skillService.removeUserSkill(userId, skillId)
                if (deleted) call.respond(HttpStatusCode.OK, ApiSuccess(data = "Đã xóa"))
                else call.respond(HttpStatusCode.NotFound, RouteApiError(message = "Không tìm thấy", code = 404))
            }
        }
    }
}