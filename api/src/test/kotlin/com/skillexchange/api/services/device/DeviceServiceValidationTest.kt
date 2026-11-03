package com.skillexchange.api.services.device

import com.skillexchange.api.plugins.ValidationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class DeviceServiceValidationTest {

    private val deviceService = DeviceService()

    @Test
    fun `registerToken - throws ValidationException on blank or whitespace token`() {
        val ex1 = assertFailsWith<ValidationException> {
            deviceService.registerToken("user-1", "", null)
        }
        assertEquals("FCM token không được để trống", ex1.message)

        val ex2 = assertFailsWith<ValidationException> {
            deviceService.registerToken("user-1", "   ", "Android Device")
        }
        assertEquals("FCM token không được để trống", ex2.message)
    }

    @Test
    fun `deleteToken - returns false on blank token without opening transaction`() {
        assertFalse(deviceService.deleteToken("user-1", ""))
        assertFalse(deviceService.deleteToken("user-1", "   "))
    }

    @Test
    fun `deleteTokenDirect - returns false on blank token without opening transaction`() {
        assertFalse(deviceService.deleteTokenDirect(""))
        assertFalse(deviceService.deleteTokenDirect("   \t"))
    }
}

