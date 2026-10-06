package com.skillexchange.api.services.notification

import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import com.skillexchange.api.services.device.DeviceService
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

@Serializable
data class FcmMessagePayload(
    val message: FcmMessage
)

@Serializable
data class FcmMessage(
    val token: String,
    val notification: FcmNotification,
    val data: Map<String, String> = emptyMap()
)

@Serializable
data class FcmNotification(
    val title: String,
    val body: String
)

interface IFcmCredentialsProvider {
    suspend fun getAccessToken(): String?
    fun getProjectId(): String?
}

class DefaultFcmCredentialsProvider : IFcmCredentialsProvider {
    private val logger = LoggerFactory.getLogger(DefaultFcmCredentialsProvider::class.java)

    private val googleCredentials: GoogleCredentials? by lazy {
        loadCredentials()
    }

    private fun loadCredentials(): GoogleCredentials? {
        val path = System.getenv("FIREBASE_SERVICE_ACCOUNT_PATH")
        val json = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON") ?: System.getenv("FIREBASE_SERVICE_ACCOUNT")

        val stream: InputStream? = when {
            !path.isNullOrBlank() && File(path).exists() -> {
                try {
                    FileInputStream(path)
                } catch (e: Exception) {
                    logger.error("Không thể đọc file credentials từ FIREBASE_SERVICE_ACCOUNT_PATH: ${e.message}")
                    null
                }
            }
            !json.isNullOrBlank() -> {
                ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))
            }
            else -> {
                logger.warn("Chưa cấu hình FIREBASE_SERVICE_ACCOUNT_PATH hoặc FIREBASE_SERVICE_ACCOUNT_JSON. Push notification sẽ ở chế độ no-op.")
                null
            }
        }

        return try {
            stream?.use {
                GoogleCredentials.fromStream(it)
                    .createScoped(listOf("https://www.googleapis.com/auth/firebase.messaging"))
            }
        } catch (e: Exception) {
            logger.error("Lỗi khi khởi tạo GoogleCredentials: ${e.message}")
            null
        }
    }

    override suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        val creds = googleCredentials ?: return@withContext null
        try {
            creds.refreshIfExpired()
            creds.accessToken?.tokenValue
        } catch (e: Exception) {
            logger.error("Lỗi khi làm mới Google OAuth access token: ${e.message}")
            null
        }
    }

    override fun getProjectId(): String? {
        val envProjectId = System.getenv("FIREBASE_PROJECT_ID")
        if (!envProjectId.isNullOrBlank()) return envProjectId
        return (googleCredentials as? ServiceAccountCredentials)?.projectId
    }
}

open class FcmService(
    private val httpClient: HttpClient,
    private val deviceService: DeviceService,
    private val credentialsProvider: IFcmCredentialsProvider = DefaultFcmCredentialsProvider(),
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val fcmBaseUrl: String = "https://fcm.googleapis.com"
) {
    private val logger = LoggerFactory.getLogger(FcmService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Gửi FCM notification bất đồng bộ, không làm gián đoạn hay thất bại luồng chính.
     */
    open fun sendPushAsync(
        targetToken: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ) {
        coroutineScope.launch {
            sendPushDirect(targetToken, title, body, data)
        }
    }

    /**
     * Gửi trực tiếp qua FCM HTTP v1 API.
     */
    open suspend fun sendPushDirect(
        targetToken: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Boolean {
        try {
            val projectId = credentialsProvider.getProjectId()
            if (projectId.isNullOrBlank()) {
                logger.warn("Không xác định được Firebase Project ID. Bỏ qua gửi FCM.")
                return false
            }

            val accessToken = credentialsProvider.getAccessToken()
            if (accessToken.isNullOrBlank()) {
                logger.warn("Không lấy được OAuth access token cho FCM. Bỏ qua gửi FCM.")
                return false
            }

            val url = "$fcmBaseUrl/v1/projects/$projectId/messages:send"
            val payload = FcmMessagePayload(
                message = FcmMessage(
                    token = targetToken,
                    notification = FcmNotification(title = title, body = body),
                    data = data
                )
            )

            val response = httpClient.post(url) {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(payload)
            }

            if (response.status == HttpStatusCode.OK) {
                return true
            }

            val responseBody = try { response.bodyAsText() } catch (_: Exception) { "" }

            // Nếu FCM báo 404 hoặc UNREGISTERED / NOT_FOUND: xóa stale token khỏi DB
            if (response.status == HttpStatusCode.NotFound ||
                responseBody.contains("UNREGISTERED") ||
                responseBody.contains("NOT_FOUND")
            ) {
                logger.warn("FCM token không còn hợp lệ hoặc chưa đăng ký (UNREGISTERED/404). Tiến hành xóa khỏi thiết bị.")
                deviceService.deleteTokenDirect(targetToken)
            } else {
                logger.error("Gửi FCM thất bại với mã HTTP ${response.status.value}")
            }
            return false
        } catch (e: Exception) {
            logger.error("Ngoại lệ khi gửi FCM notification: ${e.message}")
            return false
        }
    }
}
