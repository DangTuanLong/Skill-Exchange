package com.skillexchange.api.services.auth

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthRateLimiterTest {

    @Test
    fun `login rate limiter allows 5 requests and blocks 6th request for same email and IP`() {
        val limiter = AuthRateLimiter(maxLoginPerMinute = 5, windowMs = 60_000L)
        val email = "user@example.com"
        val ip = "192.168.1.10"

        for (i in 1..5) {
            assertTrue(limiter.tryAcquireLogin(email, ip), "Request $i should be allowed")
        }

        // 6th attempt should be blocked
        assertFalse(limiter.tryAcquireLogin(email, ip), "6th attempt should be rate limited")
    }

    @Test
    fun `login rate limiter isolates by email and IP`() {
        val limiter = AuthRateLimiter(maxLoginPerMinute = 2, windowMs = 60_000L)

        // User A on IP 1 exhausts quota
        assertTrue(limiter.tryAcquireLogin("userA@example.com", "1.1.1.1"))
        assertTrue(limiter.tryAcquireLogin("userA@example.com", "1.1.1.1"))
        assertFalse(limiter.tryAcquireLogin("userA@example.com", "1.1.1.1"))

        // User B on IP 1 should STILL be allowed (isolated by email)
        assertTrue(limiter.tryAcquireLogin("userB@example.com", "1.1.1.1"))

        // User A on IP 2 should STILL be allowed (isolated by IP)
        assertTrue(limiter.tryAcquireLogin("userA@example.com", "2.2.2.2"))
    }

    @Test
    fun `verify OTP rate limiter allows 5 requests and blocks 6th request`() {
        val limiter = AuthRateLimiter(maxOtpPerMinute = 5, windowMs = 60_000L)
        val email = "otp@example.com"
        val ip = "10.0.0.1"

        for (i in 1..5) {
            assertTrue(limiter.tryAcquireOtp(email, ip), "OTP attempt $i should be allowed")
        }

        assertFalse(limiter.tryAcquireOtp(email, ip), "6th OTP attempt should be blocked")
    }

    @Test
    fun `register rate limiter allows 3 requests per IP and blocks 4th request`() {
        val limiter = AuthRateLimiter(maxRegisterPerMinute = 3, windowMs = 60_000L)
        val ip = "203.0.113.195"

        for (i in 1..3) {
            assertTrue(limiter.tryAcquireRegister(ip), "Register attempt $i should be allowed")
        }

        assertFalse(limiter.tryAcquireRegister(ip), "4th register attempt should be blocked")
        // Different IP is still allowed
        assertTrue(limiter.tryAcquireRegister("203.0.113.196"))
    }

    @Test
    fun `cleanupExpired cleans up expired records to prevent memory leak`() {
        val limiter = AuthRateLimiter(windowMs = 100L)
        limiter.tryAcquireLogin("expire@example.com", "1.1.1.1")

        // Wait for window to expire
        Thread.sleep(150L)

        limiter.cleanupExpired(System.currentTimeMillis())

        // Should be allowed again
        assertTrue(limiter.tryAcquireLogin("expire@example.com", "1.1.1.1"))
    }

    @Test
    fun `getClientIp ignores spoofed CF-Connecting-IP header and falls back to remoteAddress`() = testApplication {
        routing {
            get("/test-ip") {
                call.respondText(call.getClientIp())
            }
        }
        val response = client.get("/test-ip") {
            header("CF-Connecting-IP", "1.2.3.4")
        }
        val ip = response.bodyAsText()
        assertFalse(ip == "1.2.3.4", "CF-Connecting-IP must not be trusted on Render")
        assertEquals("localhost", ip)
    }

    @Test
    fun `getClientIp extracts last element in X-Forwarded-For when client attempts spoofing`() = testApplication {
        routing {
            get("/test-ip") {
                call.respondText(call.getClientIp())
            }
        }
        // Client sends fake IP at the start, Render reverse proxy appends real client IP at the end
        val response = client.get("/test-ip") {
            header("X-Forwarded-For", "203.0.113.195, 198.51.100.1")
        }
        val ip = response.bodyAsText()
        assertEquals("198.51.100.1", ip)
    }

    @Test
    fun `getClientIp extracts single IP in X-Forwarded-For and trims whitespace`() = testApplication {
        routing {
            get("/test-ip") {
                call.respondText(call.getClientIp())
            }
        }
        val response = client.get("/test-ip") {
            header("X-Forwarded-For", " 10.0.0.5 ")
        }
        val ip = response.bodyAsText()
        assertEquals("10.0.0.5", ip)
    }

    @Test
    fun `getClientIp falls back to remoteAddress when X-Forwarded-For is blank or empty`() = testApplication {
        routing {
            get("/test-ip") {
                call.respondText(call.getClientIp())
            }
        }
        val response = client.get("/test-ip") {
            header("X-Forwarded-For", "   ,   ")
        }
        val ip = response.bodyAsText()
        assertEquals("localhost", ip)
    }
}
