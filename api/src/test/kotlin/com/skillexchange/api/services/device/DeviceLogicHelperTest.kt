package com.skillexchange.api.services.device

import com.skillexchange.api.plugins.ValidationException
import kotlin.test.*

class DeviceLogicHelperTest {

    @Test
    fun `validateAndCleanToken trims valid token and rejects blank`() {
        assertEquals("valid-token-123", DeviceLogicHelper.validateAndCleanToken("  valid-token-123  "))
        assertEquals("token", DeviceLogicHelper.validateAndCleanToken("token"))

        assertFailsWith<ValidationException> {
            DeviceLogicHelper.validateAndCleanToken("")
        }
        assertFailsWith<ValidationException> {
            DeviceLogicHelper.validateAndCleanToken("    ")
        }
    }

    @Test
    fun `cleanDeviceInfo trims or returns null when blank`() {
        assertEquals("Pixel 7 - Android 14", DeviceLogicHelper.cleanDeviceInfo("  Pixel 7 - Android 14  "))
        assertNull(DeviceLogicHelper.cleanDeviceInfo(null))
        assertNull(DeviceLogicHelper.cleanDeviceInfo(""))
        assertNull(DeviceLogicHelper.cleanDeviceInfo("   "))
    }

    @Test
    fun `cleanTokenOrNull trims or returns null when blank`() {
        assertEquals("fcm-tok", DeviceLogicHelper.cleanTokenOrNull("  fcm-tok  "))
        assertNull(DeviceLogicHelper.cleanTokenOrNull(""))
        assertNull(DeviceLogicHelper.cleanTokenOrNull("   "))
    }
}

