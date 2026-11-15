package com.skillexchange.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Test

class TokenManagerSessionListenerTest {

    @Test
    fun sessionChangeListener_triggeredOnSaveSessionAndClearSession() {
        val tokenManager = TokenManager(null)
        var callCount = 0
        tokenManager.setOnSessionChangedListener {
            callCount++
        }

        tokenManager.saveSession("access_1", "refresh_1", "user_1")
        assertEquals(1, callCount)

        tokenManager.saveAccessToken("access_2")
        assertEquals(2, callCount)

        tokenManager.saveRefreshToken("refresh_2")
        assertEquals(3, callCount)

        tokenManager.clearSession()
        assertEquals(4, callCount)

        tokenManager.clearAll()
        assertEquals(5, callCount)
    }
}

