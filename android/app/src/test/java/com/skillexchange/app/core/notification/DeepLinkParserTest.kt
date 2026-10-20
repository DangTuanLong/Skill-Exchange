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
    fun parse_WhenUnknownType_ReturnsNull() {
        val route = DeepLinkParser.parse(
            type = "UNKNOWN_EVENT",
            entityId = "req-123",
            isLoggedIn = true
        )

        assertNull(route)
    }
}
