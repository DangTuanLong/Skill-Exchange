package com.skillexchange.app.core.notification

import com.skillexchange.app.navigation.Screen

object DeepLinkParser {

    /**
     * Phân tích payload deep link theo BUSINESS_RULES.md §6.
     * Bắt buộc kiểm tra đăng nhập (isLoggedIn) trước khi điều hướng.
     * Payload deep link chỉ gồm type và entityId.
     */
    fun parse(type: String?, entityId: String?, isLoggedIn: Boolean): String? {
        if (!isLoggedIn) return null
        if (entityId.isNullOrBlank()) return null

        return when (type?.trim()?.uppercase()) {
            "NEW_MESSAGE" -> Screen.ChatDetail.createRoute(entityId)
            "NEW_REQUEST",
            "REQUEST_ACCEPTED",
            "REQUEST_REJECTED",
            "APPOINTMENT_REMINDER" -> Screen.BookingDetail.createRoute(entityId)
            "RATING_REMINDER" -> Screen.Rating.createRoute(entityId)
            else -> null
        }
    }
}
