package com.skillexchange.api.services.exchange

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RescheduleRateLimiterTest {

    @Test
    fun `allows up to 5 requests per minute and blocks 6th request`() {
        val limiter = RescheduleRateLimiter(maxPerMinute = 5)
        val user = "user-test"

        for (i in 1..5) {
            assertTrue(limiter.tryAcquire(user), "Request $i should be allowed")
        }

        assertFalse(limiter.tryAcquire(user), "6th request should be rate limited")
    }

    @Test
    fun `resets allows new requests after reset`() {
        val limiter = RescheduleRateLimiter(maxPerMinute = 2)
        val user = "user-test"

        assertTrue(limiter.tryAcquire(user))
        assertTrue(limiter.tryAcquire(user))
        assertFalse(limiter.tryAcquire(user))

        limiter.reset()

        assertTrue(limiter.tryAcquire(user), "Should be allowed after reset")
    }
}

