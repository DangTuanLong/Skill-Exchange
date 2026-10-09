package com.skillexchange.api.services.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.google.auth.oauth2.ServiceAccountCredentials
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.interfaces.RSAPrivateKey
import java.util.Date

interface IFirebaseTokenService {
    fun createCustomToken(userId: String): String
}

/**
 * Service tạo Firebase Custom Token cho người dùng theo đặc tả chính thức của Google:
 * "Create Custom Tokens using a third-party JWT library"
 * (https://firebase.google.com/docs/auth/admin/create-custom-tokens#create_custom_tokens_using_a_third-party_jwt_library)
 *
 * Quyết định kiến trúc DEC-016: Sử dụng thư viện com.auth0:java-jwt đã có sẵn trong dự án
 * để ký RS256 JWT, tránh kéo thêm firebase-admin (+44.2 MB jar, build kéo dài 2 phút).
 */
class FirebaseTokenService(
    credentialsStream: InputStream? = null
) : IFirebaseTokenService {

    private val logger = LoggerFactory.getLogger(FirebaseTokenService::class.java)

    private val serviceAccountCredentials: ServiceAccountCredentials? by lazy {
        loadCredentials(credentialsStream)
    }

    private fun loadCredentials(providedStream: InputStream?): ServiceAccountCredentials? {
        if (providedStream != null) {
            return try {
                ServiceAccountCredentials.fromStream(providedStream)
            } catch (e: Exception) {
                logger.error("Lỗi khi khởi tạo ServiceAccountCredentials từ stream cung cấp: ${e.message}")
                null
            }
        }

        val path = System.getenv("FIREBASE_SERVICE_ACCOUNT_PATH")
        val json = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON") ?: System.getenv("FIREBASE_SERVICE_ACCOUNT")

        val candidateFiles = listOfNotNull(
            path?.takeIf { it.isNotBlank() }?.let { File(it) },
            File("api/firebase-service-account.json"),
            File("firebase-service-account.json"),
            File("../firebase-service-account.json"),
            File("../api/firebase-service-account.json")
        )
        val existingFile = candidateFiles.firstOrNull { it.exists() }

        val stream: InputStream? = when {
            existingFile != null -> {
                try {
                    FileInputStream(existingFile)
                } catch (e: Exception) {
                    logger.error("Không thể đọc file credentials từ ${existingFile.path}: ${e.message}")
                    null
                }
            }
            !json.isNullOrBlank() -> {
                ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))
            }
            else -> {
                logger.warn("Chưa cấu hình FIREBASE_SERVICE_ACCOUNT_PATH hoặc FIREBASE_SERVICE_ACCOUNT_JSON.")
                null
            }
        }

        return try {
            stream?.use {
                ServiceAccountCredentials.fromStream(it)
            }
        } catch (e: Exception) {
            logger.error("Lỗi khi nạp ServiceAccountCredentials: ${e.message}")
            null
        }
    }

    override fun createCustomToken(userId: String): String {
        require(userId.isNotBlank()) { "userId không được để trống" }
        val creds = serviceAccountCredentials
            ?: throw IllegalStateException("Firebase Service Account chưa được cấu hình trên máy chủ")

        val clientEmail = creds.clientEmail
        val privateKey = creds.privateKey as? RSAPrivateKey
            ?: throw IllegalStateException("Private key của service account không phải là RSAPrivateKey hợp lệ")

        val nowMs = System.currentTimeMillis()
        val issuedAt = Date(nowMs)
        val expiresAt = Date(nowMs + 3600 * 1000) // 1 giờ (3600s)

        val algorithm = Algorithm.RSA256(null, privateKey)

        // Đối chiếu chính xác theo đặc tả của Google:
        // - iss: client_email của service account
        // - sub: client_email của service account
        // - aud: "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit"
        // - iat: epoch seconds
        // - exp: epoch seconds (tối đa 3600s)
        // - uid: Supabase user ID (UUID)
        return JWT.create()
            .withIssuer(clientEmail)
            .withSubject(clientEmail)
            .withAudience("https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit")
            .withIssuedAt(issuedAt)
            .withExpiresAt(expiresAt)
            .withClaim("uid", userId)
            .sign(algorithm)
    }
}
