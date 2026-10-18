package com.skillexchange.api.plugins

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class SecurityHeadersTest {

    @Test
    fun `HTTP responses include X-Content-Type-Options nosniff and X-Frame-Options DENY`() = testApplication {
        application {
            configureSecurityHeaders()
            routing {
                get("/test-headers") {
                    call.respondText("OK")
                }
            }
        }

        val response = client.get("/test-headers")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("nosniff", response.headers["X-Content-Type-Options"])
        assertEquals("DENY", response.headers["X-Frame-Options"])
    }
}
