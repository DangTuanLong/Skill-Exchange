package com.skillexchange.api.services.notification

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderRateLimiterTest {

    @Test
    fun `allows up to 10 requests per minute and blocks 11th request`() {
        val limiter = ReminderRateLimiter(maxPerMinute = 10, windowMs = 60_000L)

        for (i in 1..10) {
            assertTrue(limiter.tryAcquire(), "Request $i should be allowed")
        }

        assertFalse(limiter.tryAcquire(), "11th request should be rate limited")
    }

    @Test
    fun `reset clears timestamps allowing new requests`() {
        val limiter = ReminderRateLimiter(maxPerMinute = 2, windowMs = 60_000L)

        assertTrue(limiter.tryAcquire())
        assertTrue(limiter.tryAcquire())
        assertFalse(limiter.tryAcquire())

        limiter.reset()

        assertTrue(limiter.tryAcquire(), "Should allow request after reset")
    }
}
