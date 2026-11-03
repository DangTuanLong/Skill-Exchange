package com.skillexchange.api.services.call

import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.call.CallTokenRequest
import com.skillexchange.api.plugins.ValidationException
import io.mockk.mockk
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CallServiceValidationTest {

    private val mockLiveKitTokenService = mockk<LiveKitTokenService>(relaxed = true)
    private val callService = CallService(liveKitTokenService = mockLiveKitTokenService)

    @Test
    fun `initiateCall - throws ValidationException on invalid callType`() {
        val req = CallTokenRequest(
            exchangeId = UUID.randomUUID().toString(),
            callType = "INVALID_TYPE"
        )

        val ex = assertFailsWith<ValidationException> {
            callService.initiateCall("caller-1", req)
        }
        assertEquals("Loại cuộc gọi không hợp lệ (phải là VOICE hoặc VIDEO)", ex.message)
    }

    @Test
    fun `initiateCall - throws ValidationException on invalid exchangeId UUID format`() {
        val req = CallTokenRequest(
            exchangeId = "not-a-valid-uuid",
            callType = "VOICE"
        )

        val ex = assertFailsWith<ValidationException> {
            callService.initiateCall("caller-1", req)
        }
        assertEquals("exchangeId không hợp lệ", ex.message)
    }

    @Test
    fun `initiateCall - enforces 5 req per min rate limit before opening transaction`() {
        val req = CallTokenRequest(
            exchangeId = UUID.randomUUID().toString(),
            callType = "VOICE"
        )
        val testUser = "caller-rate-limit-user"

        // First 5 attempts pass checkRateLimit and fail at transaction (no DB)
        for (i in 1..5) {
            try {
                callService.initiateCall(testUser, req)
            } catch (_: Exception) {
                // Expected Exposed transaction exception
            }
        }

        // 6th attempt must be rejected by checkRateLimit before reaching transaction
        val ex = assertFailsWith<CallRateLimitException> {
            callService.initiateCall(testUser, req)
        }
        assertEquals(ErrorCodes.RATE_LIMITED, ex.errorCode)
        assertEquals("Bạn đã yêu cầu tạo cuộc gọi quá nhiều lần. Vui lòng đợi 1 phút.", ex.message)
    }
}
