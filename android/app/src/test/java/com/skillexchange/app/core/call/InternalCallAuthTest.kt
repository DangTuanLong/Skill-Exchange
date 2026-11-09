package com.skillexchange.app.core.call

import com.skillexchange.app.core.service.CallForegroundService
import com.skillexchange.app.domain.model.call.CallToken
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InternalCallAuthTest {

    @Before
    fun setUp() {
        InternalCallAuth.clear()
    }

    @After
    fun tearDown() {
        InternalCallAuth.clear()
    }

    @Test
    fun isAuthorized_WhenUnauthorized_ReturnsFalse() {
        // Giả lập intent giả mạo từ bên thứ ba với sessionId tùy ý
        assertFalse(InternalCallAuth.isAuthorized("fake-session-uuid"))
    }

    @Test
    fun isAuthorized_WhenNullOrBlankSessionId_ReturnsFalse() {
        assertFalse(InternalCallAuth.isAuthorized(null))
        assertFalse(InternalCallAuth.isAuthorized(""))
        assertFalse(InternalCallAuth.isAuthorized("   "))
    }

    @Test
    fun isAuthorized_WhenAuthorized_ReturnsTrueOnceAndClears() {
        // Cấp quyền nội bộ sau khi bấm Trả lời trong IncomingCallActivity
        val validSession = "session-authorized-123"
        InternalCallAuth.authorize(validSession)

        // Lần kiểm tra đầu tiên hợp lệ
        assertTrue(InternalCallAuth.isAuthorized(validSession))

        // Sau khi đã tiêu thụ quyền (consumed), lần tiếp theo phải trả về false
        assertFalse(InternalCallAuth.isAuthorized(validSession))
    }

    @Test
    fun isAuthorized_WhenMismatchSessionId_ReturnsFalse() {
        InternalCallAuth.authorize("valid-session-123")
        assertFalse(InternalCallAuth.isAuthorized("attacker-session-456"))
    }

    @Test
    fun consumeJoinedToken_ReturnsCachedTokenOnceForMatchingSession() {
        val sampleToken = CallToken(
            url = "wss://livekit.example.com",
            roomName = "room-123",
            token = "jwt-livekit",
            callType = com.skillexchange.app.domain.model.call.CallType.VOICE,
            sessionId = "session-123"
        )
        InternalCallAuth.cacheJoinedToken("session-123", sampleToken)

        val consumed = InternalCallAuth.consumeJoinedToken("session-123")
        assertEquals(sampleToken, consumed)

        // Lần gọi thứ hai phải trả về null vì đã được tiêu thụ
        assertNull(InternalCallAuth.consumeJoinedToken("session-123"))
    }
}

