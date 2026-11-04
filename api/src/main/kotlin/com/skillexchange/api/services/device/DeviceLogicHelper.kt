package com.skillexchange.api.services.device

import com.skillexchange.api.plugins.ValidationException

/**
 * Pure logic helper for FCM device token trimming and validation.
 */
object DeviceLogicHelper {

    fun validateAndCleanToken(token: String): String {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) {
            throw ValidationException("FCM token không được để trống")
        }
        return cleanToken
    }

    fun cleanDeviceInfo(deviceInfo: String?): String? {
        return deviceInfo?.trim()?.takeIf { it.isNotBlank() }
    }

    fun cleanTokenOrNull(token: String): String? {
        return token.trim().takeIf { it.isNotBlank() }
    }
}

