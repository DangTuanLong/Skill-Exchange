package com.skillexchange.api.services.call

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.skillexchange.api.models.ErrorCodes
import java.security.SecureRandom
import java.util.Date

open class LiveKitConfigException(
    message: String,
    val errorCode: String = ErrorCodes.LIVEKIT_NOT_CONFIGURED
) : RuntimeException(message)

/**
 * Service tạo LiveKit Room Access Token theo đặc tả chuẩn của LiveKit Cloud.
 * Tài liệu tham khảo chính thức: https://docs.livekit.io/home/get-started/authentication/
 */
open class LiveKitTokenService(
    private val serverUrl: String? = System.getenv("LIVEKIT_URL"),
    private val apiKey: String? = System.getenv("LIVEKIT_API_KEY"),
    private val apiSecret: String? = System.getenv("LIVEKIT_API_SECRET"),
    private val secureRandom: SecureRandom = SecureRandom()
) {

    fun isConfigured(): Boolean {
        return !serverUrl.isNullOrBlank() && !apiKey.isNullOrBlank() && !apiSecret.isNullOrBlank()
    }

    fun getServerUrl(): String {
        return serverUrl?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw LiveKitConfigException("Biến môi trường LIVEKIT_URL chưa được cấu hình")
    }

    /**
     * Sinh tên phòng ngẫu nhiên sử dụng SecureRandom 16 bytes (32 hex chars),
     * hoàn toàn không chứa exchangeId để bảo vệ quyền riêng tư và tránh đoán tên phòng.
     */
    open fun generateRoomName(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        val hex = bytes.joinToString("") { "%02x".format(it) }
        return "call_$hex"
    }

    /**
     * Ký JWT Access Token HS256 cho LiveKit với quyền tối thiểu (Least Privilege):
     * - Chỉ tham gia đúng phòng roomName
     * - canPublish = true, canSubscribe = true, canPublishData = true
     * - roomAdmin = false, roomCreate = false
     * - Thời hạn sống token: 10 phút (600 giây)
     */
    open fun createJoinToken(
        userId: String,
        userName: String,
        roomName: String
    ): String {
        if (!isConfigured()) {
            throw LiveKitConfigException(
                "Dịch vụ gọi trực tiếp chưa được kích hoạt do thiếu biến môi trường LIVEKIT_URL, LIVEKIT_API_KEY hoặc LIVEKIT_API_SECRET."
            )
        }

        val now = System.currentTimeMillis()
        val issuedAt = Date(now)
        val expiresAt = Date(now + 600_000L) // 10 phút (600s)

        val videoGrants = mapOf(
            "room" to roomName,
            "roomJoin" to true,
            "canPublish" to true,
            "canSubscribe" to true,
            "canPublishData" to true,
            "roomAdmin" to false,
            "roomCreate" to false
        )

        return JWT.create()
            .withIssuer(apiKey!!)
            .withSubject(userId)
            .withClaim("name", userName)
            .withClaim("video", videoGrants)
            .withIssuedAt(issuedAt)
            .withNotBefore(issuedAt)
            .withExpiresAt(expiresAt)
            .sign(Algorithm.HMAC256(apiSecret!!))
    }
}
