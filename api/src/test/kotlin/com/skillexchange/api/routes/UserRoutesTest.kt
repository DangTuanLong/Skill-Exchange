package com.skillexchange.api.routes

import com.skillexchange.api.plugins.configureSerialization
import com.skillexchange.api.plugins.configureStatusPages
import com.skillexchange.api.services.UserService
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class UserRoutesTest {

    private val mockUserService = mockk<UserService>(relaxed = true)

    @Test
    fun `GET users search clamps limit above 50 down to 50`() = testApplication {
        every {
            mockUserService.searchUsers(any(), any(), any(), any(), any(), 50, any())
        } returns emptyList()

        application {
            configureSerialization()
            configureStatusPages()
            routing {
                userRoutes(mockUserService)
            }
        }

        val response = client.get("/api/users/search?limit=100")
        assertEquals(HttpStatusCode.OK, response.status)

        verify {
            mockUserService.searchUsers(null, null, null, null, null, 50, 0)
        }
    }

    @Test
    fun `GET users search clamps limit below 1 up to 1`() = testApplication {
        every {
            mockUserService.searchUsers(any(), any(), any(), any(), any(), 1, any())
        } returns emptyList()

        application {
            configureSerialization()
            configureStatusPages()
            routing {
                userRoutes(mockUserService)
            }
        }

        val response = client.get("/api/users/search?limit=0")
        assertEquals(HttpStatusCode.OK, response.status)

        verify {
            mockUserService.searchUsers(null, null, null, null, null, 1, 0)
        }
    }
}
