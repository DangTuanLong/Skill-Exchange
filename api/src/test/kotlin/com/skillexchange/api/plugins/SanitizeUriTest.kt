package com.skillexchange.api.plugins

import kotlin.test.Test
import kotlin.test.assertEquals

class SanitizeUriTest {

    @Test
    fun `sanitizeUri masks sensitive query parameters in URL`() {
        val original = "/api/devices?token=fcm_token_super_secret_12345"
        val sanitized = sanitizeUri(original)
        assertEquals("/api/devices?token=***", sanitized)
    }

    @Test
    fun `sanitizeUri masks secret and password query parameters`() {
        val original = "/api/test?secret=my_secret_key&other=allowed&password=my_password"
        val sanitized = sanitizeUri(original)
        assertEquals("/api/test?secret=***&other=allowed&password=***", sanitized)
    }

    @Test
    fun `sanitizeUri does not alter normal query parameters`() {
        val original = "/api/notifications?page=1&limit=30"
        val sanitized = sanitizeUri(original)
        assertEquals(original, sanitized)
    }
}
