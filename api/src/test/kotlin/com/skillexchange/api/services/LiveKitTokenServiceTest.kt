package com.skillexchange.api.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.services.call.LiveKitConfigException
import com.skillexchange.api.services.call.LiveKitTokenService
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LiveKitTokenServiceTest {

    @Test
    fun `isConfigured returns false when any env is missing or blank`() {
        val service1 = LiveKitTokenService(serverUrl = null, apiKey = "key", apiSecret = "secret")
        assertFalse(service1.isConfigured())

        val service2 = LiveKitTokenService(serverUrl = "wss://test.livekit.cloud", apiKey = "", apiSecret = "secret")
        assertFalse(service2.isConfigured())

        val service3 = LiveKitTokenService(serverUrl = "wss://test.livekit.cloud", apiKey = "key", apiSecret = "   ")
        assertFalse(service3.isConfigured())
    }

    @Test
    fun `isConfigured returns true when all credentials are set`() {
        val service = LiveKitTokenService(
            serverUrl = "wss://test.livekit.cloud",
            apiKey = "API_TEST_KEY",
            apiSecret = "API_TEST_SECRET_1234567890"
        )
        assertTrue(service.isConfigured())
        assertEquals("wss://test.livekit.cloud", service.getServerUrl())
    }

    @Test
    fun `createJoinToken throws LiveKitConfigException if unconfigured`() {
        val service = LiveKitTokenService(serverUrl = null, apiKey = null, apiSecret = null)
        val ex = assertFailsWith<LiveKitConfigException> {
            service.createJoinToken("user-1", "Long", "call_room1")
        }
        assertEquals(ErrorCodes.LIVEKIT_NOT_CONFIGURED, ex.errorCode)
    }

    @Test
    fun `generateRoomName creates random hex name starting with call_ and not containing exchangeId`() {
        val service = LiveKitTokenService(
            serverUrl = "wss://test.livekit.cloud",
            apiKey = "API_KEY",
            apiSecret = "API_SECRET"
        )

        val room1 = service.generateRoomName()
        val room2 = service.generateRoomName()

        assertTrue(room1.startsWith("call_"), "Room name must start with 'call_'")
        assertTrue(room2.startsWith("call_"), "Room name must start with 'call_'")
        assertEquals(37, room1.length, "call_ (5) + 16 bytes hex (32) = 37 chars")
        assertNotEquals(room1, room2, "Successive room names must be randomly unique")
    }

    @Test
    fun `createJoinToken signs valid HS256 JWT with correct VideoGrants and 10min expiration`() {
        val apiKey = "API_TEST_KEY_123"
        val apiSecret = "MY_VERY_SECRET_LIVEKIT_KEY_9999"
        val service = LiveKitTokenService(
            serverUrl = "wss://demo.livekit.cloud",
            apiKey = apiKey,
            apiSecret = apiSecret
        )

        val userId = "user-alice-111"
        val userName = "Alice Nguyen"
        val roomName = "call_abc123456789"

        val tokenString = service.createJoinToken(userId, userName, roomName)
        assertNotNull(tokenString)

        // Verify token using same algorithm
        val verifier = JWT.require(Algorithm.HMAC256(apiSecret))
            .withIssuer(apiKey)
            .build()
        val decoded = verifier.verify(tokenString)

        assertEquals(apiKey, decoded.issuer)
        assertEquals(userId, decoded.subject)
        assertEquals(userName, decoded.getClaim("name").asString())

        // Check VideoGrants claim
        val videoClaim = decoded.getClaim("video").asMap()
        assertNotNull(videoClaim)
        assertEquals(roomName, videoClaim["room"])
        assertEquals(true, videoClaim["roomJoin"])
        assertEquals(true, videoClaim["canPublish"])
        assertEquals(true, videoClaim["canSubscribe"])
        assertEquals(true, videoClaim["canPublishData"])
        assertEquals(false, videoClaim["roomAdmin"])
        assertEquals(false, videoClaim["roomCreate"])

        // Check expiration is 10 minutes (600 seconds) from iat
        val iatSeconds = decoded.issuedAt.time / 1000
        val expSeconds = decoded.expiresAt.time / 1000
        assertEquals(600L, expSeconds - iatSeconds, "Token must expire in exactly 600s (10 min)")
    }
}
