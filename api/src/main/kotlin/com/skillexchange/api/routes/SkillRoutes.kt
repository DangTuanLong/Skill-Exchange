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
            call.respond(mapOf("success" to true, "data" to categories))
        }

        // GET /api/skills/search - public/authenticated user search
        get("/search") {
            val q = call.request.queryParameters["q"]
            val categoryId = call.request.queryParameters["category"]?.toIntOrNull()
            val city = call.request.queryParameters["city"]
            val minProficiency = call.request.queryParameters["minProficiency"]?.toIntOrNull()
            val type = call.request.queryParameters["type"]
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

            val results = skillService.searchDiscovery(
                query = q,
                categoryId = categoryId,
                city = city,
                minProficiency = minProficiency,
                type = type,
                limit = limit,
                offset = offset
            )
            call.respond(mapOf("success" to true, "data" to results))
        }

        // GET /api/skills?category=1
        get {
            val categoryId = call.request.queryParameters["category"]?.toIntOrNull()
            val skills = if (categoryId != null) skillService.getSkillsByCategory(categoryId)
                         else emptyList()
            call.respond(mapOf("success" to true, "data" to skills))
        }

        authenticate("auth-jwt") {

            // GET /api/skills/user - my skills
            get("/user") {
                val userId = call.getUserId()
                val skills = skillService.getUserSkills(userId)
                call.respond(mapOf("success" to true, "data" to skills))
            }

            // GET /api/skills/user/{userId} - other user's skills
            get("/user/{userId}") {
                val userId = call.parameters["userId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                val skills = skillService.getUserSkills(userId)
                call.respond(mapOf("success" to true, "data" to skills))
            }

            // POST /api/skills/user - add skill
            post("/user") {
                val userId = call.getUserId()
                val req = call.receive<AddUserSkillRequest>()
                if (req.type !in listOf("HAVE", "WANT")) {
                    return@post call.respond(HttpStatusCode.BadRequest,
                        mapOf("success" to false, "message" to "type pháº£i lÃ  HAVE hoáº·c WANT"))
                }
                val skill = skillService.addUserSkill(userId, req)
                call.respond(HttpStatusCode.Created, mapOf("success" to true, "data" to skill))
            }

            // DELETE /api/skills/user/{id}
            delete("/user/{id}") {
                val userId = call.getUserId()
                val skillId = call.parameters["id"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val deleted = skillService.removeUserSkill(userId, skillId)
                if (deleted) call.respond(mapOf("success" to true, "message" to "ÄÃ£ xÃ³a"))
                else call.respond(HttpStatusCode.NotFound, mapOf("success" to false, "message" to "KhÃ´ng tÃ¬m tháº¥y"))
            }
        }
    }
}