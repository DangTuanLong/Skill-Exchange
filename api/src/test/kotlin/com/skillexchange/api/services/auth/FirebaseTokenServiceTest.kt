package com.skillexchange.api.services.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.io.ByteArrayInputStream
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FirebaseTokenServiceTest {

    private fun generateTestServiceAccountJson(): Pair<String, RSAPublicKey> {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        val keyPair = keyGen.generateKeyPair()

        val base64Key = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(keyPair.private.encoded)
        val pemKey = "-----BEGIN PRIVATE KEY-----\n$base64Key\n-----END PRIVATE KEY-----\n"

        val json = """
            {
              "type": "service_account",
              "project_id": "test-firebase-proj",
              "private_key_id": "mock_key_id_123",
              "private_key": "${pemKey.replace("\n", "\\n")}",
              "client_email": "firebase-sa@test-firebase-proj.iam.gserviceaccount.com",
              "client_id": "100000000000000000001",
              "auth_uri": "https://accounts.google.com/o/oauth2/auth",
              "token_uri": "https://oauth2.googleapis.com/token"
            }
        """.trimIndent()

        return Pair(json, keyPair.public as RSAPublicKey)
    }

    @Test
    fun `createCustomToken produces valid RS256 signed JWT according to Google specification`() {
        val (saJson, publicKey) = generateTestServiceAccountJson()
        val service = FirebaseTokenService(ByteArrayInputStream(saJson.toByteArray()))
        val targetUserId = "4d6d6741-ff44-48d1-bc76-efdbb7c01111"

        val token = service.createCustomToken(targetUserId)
        assertNotNull(token)
        assertTrue(token.isNotBlank())

        // Verify with Algorithm.RSA256 using matching public key
        val verifier = JWT.require(Algorithm.RSA256(publicKey, null))
            .withIssuer("firebase-sa@test-firebase-proj.iam.gserviceaccount.com")
            .withSubject("firebase-sa@test-firebase-proj.iam.gserviceaccount.com")
            .withAudience("https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit")
            .withClaim("uid", targetUserId)
            .build()

        val decoded = verifier.verify(token)

        assertEquals("RS256", decoded.algorithm)
        assertEquals("JWT", decoded.type)
        assertEquals("firebase-sa@test-firebase-proj.iam.gserviceaccount.com", decoded.issuer)
        assertEquals("firebase-sa@test-firebase-proj.iam.gserviceaccount.com", decoded.subject)
        assertTrue(decoded.audience.contains("https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit"))
        assertEquals(targetUserId, decoded.getClaim("uid").asString())

        // Verify expiration is <= 3600 seconds from issuedAt
        val iat = decoded.issuedAt
        val exp = decoded.expiresAt
        assertNotNull(iat)
        assertNotNull(exp)
        val diffSeconds = (exp.time - iat.time) / 1000
        assertEquals(3600L, diffSeconds)
    }

    @Test
    fun `createCustomToken throws IllegalArgumentException when userId is blank`() {
        val (saJson, _) = generateTestServiceAccountJson()
        val service = FirebaseTokenService(ByteArrayInputStream(saJson.toByteArray()))

        assertFailsWith<IllegalArgumentException> {
            service.createCustomToken("")
        }

        assertFailsWith<IllegalArgumentException> {
            service.createCustomToken("   ")
        }
    }
}
