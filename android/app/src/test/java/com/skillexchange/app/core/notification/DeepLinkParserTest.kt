package com.skillexchange.app.core.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkParserTest {

    @Test
    fun parse_WhenValidNewRequestAndLoggedIn_ReturnsBookingDetailRoute() {
        val route = DeepLinkParser.parse(
            type = "NEW_REQUEST",
            entityId = "req-123-uuid",
            isLoggedIn = true
        )

        assertEquals("booking_detail/req-123-uuid", route)
    }

    @Test
    fun parse_WhenNotLoggedIn_ReturnsNull() {
        // BUSINESS_RULES.md §6: phải kiểm tra auth trước khi mở qua deep link
        val route = DeepLinkParser.parse(
            type = "NEW_REQUEST",
            entityId = "req-123-uuid",
            isLoggedIn = false
        )

        assertNull(route)
    }

    @Test
    fun parse_WhenBlankOrNullEntityId_ReturnsNull() {
        assertNull(DeepLinkParser.parse("NEW_REQUEST", null, true))
        assertNull(DeepLinkParser.parse("NEW_REQUEST", "   ", true))
    }

    @Test
    fun parse_WhenValidNewMessage_ReturnsChatDetailRoute() {
        val route = DeepLinkParser.parse(
            type = "NEW_MESSAGE",
            entityId = "chat-456",
            isLoggedIn = true
        )
        assertEquals("chat_detail/chat-456", route)
    }

    @Test
    fun parse_WhenRequestAcceptedOrRejected_ReturnsBookingDetailRoute() {
        assertEquals(
            "booking_detail/exchange-1",
            DeepLinkParser.parse("REQUEST_ACCEPTED", "exchange-1", true)
        )
        assertEquals(
            "booking_detail/exchange-2",
            DeepLinkParser.parse("REQUEST_REJECTED", "exchange-2", true)
        )
    }

    @Test
    fun parse_WhenAppointmentReminder_ReturnsBookingDetailRoute() {
        val route = DeepLinkParser.parse(
            type = "APPOINTMENT_REMINDER",
            entityId = "exchange-789",
            isLoggedIn = true
        )
        assertEquals("booking_detail/exchange-789", route)
    }

    @Test
    fun parse_WhenRatingReminder_ReturnsRatingRoute() {
        val route = DeepLinkParser.parse(
            type = "RATING_REMINDER",
            entityId = "exchange-999",
            isLoggedIn = true
        )
        assertEquals("rating/exchange-999", route)
    }

    @Test
    fun parse_WhenMissedCall_ReturnsChatDetailRoute() {
        // TASK-080 Mục 6/7: MISSED_CALL điều hướng đến ChatDetail để người dùng xem và gọi lại chủ động
        val route = DeepLinkParser.parse(
            type = "MISSED_CALL",
            entityId = "exchange-call-123",
            isLoggedIn = true
        )
        assertEquals("chat_detail/exchange-call-123", route)
    }

    @Test
    fun parse_WhenCallDeepLink_ReturnsNullAndDoesNotDial() {
        // TASK-080 Mục 1: Deep link type=CALL từ bên ngoài không tự quay số hay điều hướng trực tiếp
        val route = DeepLinkParser.parse(
            type = "CALL",
            entityId = "exchange-call-456",
            isLoggedIn = true
        )
        assertNull("Deep link CALL từ nguồn ngoài phải trả về null để ngăn tự ý quay số", route)
    }

    @Test
    fun parse_WhenFakeCallIntent_NeverReturnsCallRoute() {
        // Kiểm thử Intent giả mạo từ ứng dụng bên thứ 3
        val route = DeepLinkParser.parse(
            type = "call",
            entityId = "fake-exchange-id",
            isLoggedIn = true
        )
        assertNull(route)
    }

    @Test
    fun parse_WhenUnknownType_ReturnsNull() {
        val route = DeepLinkParser.parse(
            type = "UNKNOWN_EVENT",
            entityId = "req-123",
            isLoggedIn = true
        )

        assertNull(route)
    }
}
