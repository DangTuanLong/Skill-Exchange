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

        return when (type) {
            "NEW_REQUEST" -> Screen.BookingDetail.createRoute(entityId)
            else -> null
        }
    }
}
