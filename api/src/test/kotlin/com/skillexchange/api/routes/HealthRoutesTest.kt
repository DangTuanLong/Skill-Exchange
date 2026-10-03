package com.skillexchange.api.routes

import com.skillexchange.api.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HealthRoutesTest {

    @Test
    fun `GET health returns 200 OK with status ok`() = testApplication {
        application {
            configureSerialization()
            routing {
                healthRoutes()
            }
        }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"status\":\"ok\""))
    }

    @Test
    fun `GET root returns 200 OK with running message`() = testApplication {
        application {
            configureSerialization()
            routing {
                healthRoutes()
            }
        }

        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("SkillExchange API is running"))
    }
}
