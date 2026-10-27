package com.skillexchange.api.services.chat

import com.skillexchange.api.models.exchange.ExchangeRequestDto
import com.skillexchange.api.services.notification.IFcmCredentialsProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.slf4j.LoggerFactory
import java.time.Instant

interface IFirestoreChatService {
    fun createChatRoomAsync(exchange: ExchangeRequestDto)
    fun updateChatStatusAsync(exchangeId: String, status: String)
    fun recordSystemCallMessageAsync(
        exchangeId: String,
        callType: String,
        callStatus: String,
        durationSeconds: Int,
        callerId: String,
        receiverId: String
    )
}

open class FirestoreChatService(
    private val httpClient: HttpClient,
    private val credentialsProvider: IFcmCredentialsProvider,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : IFirestoreChatService {

    private val logger = LoggerFactory.getLogger(FirestoreChatService::class.java)

    override fun recordSystemCallMessageAsync(
        exchangeId: String,
        callType: String,
        callStatus: String,
        durationSeconds: Int,
        callerId: String,
        receiverId: String
    ) {
        scope.launch {
            try {
                val projectId = credentialsProvider.getProjectId() ?: return@launch
                val accessToken = credentialsProvider.getAccessToken() ?: return@launch

                val nowIso = Instant.now().toString()
                val messageId = java.util.UUID.randomUUID().toString()

                val callTypeUpper = callType.trim().uppercase()
                val isVideo = callTypeUpper == "VIDEO"
                val callTypeLabel = if (isVideo) "Cuộc gọi video" else "Cuộc gọi thoại"

                val content = when (callStatus.trim().uppercase()) {
                    "ENDED", "COMPLETED" -> {
                        val mm = durationSeconds / 60
                        val ss = durationSeconds % 60
                        val timeStr = "%02d:%02d".format(mm, ss)
                        "$callTypeLabel ($timeStr)"
                    }
                    "REJECTED" -> "Cuộc gọi bị từ chối"
                    "MISSED" -> "Cuộc gọi nhỡ"
                    "BUSY" -> "Máy bận"
                    else -> "$callTypeLabel đã kết thúc"
                }

                // 1. Tạo tin nhắn SYSTEM_CALL trong subcollection /chats/{exchangeId}/messages/{messageId}
                val msgFields = buildJsonObject {
                    put("type", buildJsonObject { put("stringValue", "SYSTEM_CALL") })
                    put("callType", buildJsonObject { put("stringValue", callTypeUpper) })
                    put("callStatus", buildJsonObject { put("stringValue", callStatus.trim().uppercase()) })
                    put("durationSeconds", buildJsonObject { put("integerValue", durationSeconds.toString()) })
                    put("callerId", buildJsonObject { put("stringValue", callerId) })
                    put("receiverId", buildJsonObject { put("stringValue", receiverId) })
                    put("senderId", buildJsonObject { put("stringValue", callerId) })
                    put("content", buildJsonObject { put("stringValue", content) })
                    put("createdAt", buildJsonObject { put("timestampValue", nowIso) })
                    put("participants", buildJsonObject {
                        put("arrayValue", buildJsonObject {
                            putJsonArray("values") {
                                add(buildJsonObject { put("stringValue", callerId) })
                                add(buildJsonObject { put("stringValue", receiverId) })
                            }
                        })
                    })
                }
                val msgPayload = buildJsonObject { put("fields", msgFields) }
                val msgUrl = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/chats/$exchangeId/messages?documentId=$messageId"

                val msgResp = httpClient.post(msgUrl) {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                    contentType(ContentType.Application.Json)
                    setBody(msgPayload.toString())
                }

                if (msgResp.status != HttpStatusCode.OK) {
                    logger.warn("Ghi tin nhắn SYSTEM_CALL Firestore thất bại (${msgResp.status}): ${msgResp.bodyAsText()}")
                }

                // 2. Cập nhật lastMessage của phòng chat cha
                val chatFields = buildJsonObject {
                    put("lastMessage", buildJsonObject { put("stringValue", content) })
                    put("lastMessageAt", buildJsonObject { put("timestampValue", nowIso) })
                    put("lastSenderId", buildJsonObject { put("stringValue", callerId) })
                    put("updatedAt", buildJsonObject { put("timestampValue", nowIso) })
                }
                val chatPayload = buildJsonObject { put("fields", chatFields) }
                val chatUrl = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/chats/$exchangeId?updateMask.fieldPaths=lastMessage&updateMask.fieldPaths=lastMessageAt&updateMask.fieldPaths=lastSenderId&updateMask.fieldPaths=updatedAt"

                httpClient.patch(chatUrl) {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                    contentType(ContentType.Application.Json)
                    setBody(chatPayload.toString())
                }
            } catch (e: Throwable) {
                // Lỗi ghi Firestore chỉ ghi log, không làm hỏng nghiệp vụ kết thúc cuộc gọi
                logger.error("Lỗi khi ghi nhận tin nhắn SYSTEM_CALL Firestore cho exchange $exchangeId: ${e.message}")
            }
        }
    }

    override fun createChatRoomAsync(exchange: ExchangeRequestDto) {
        scope.launch {
            try {
                val projectId = credentialsProvider.getProjectId()
                if (projectId.isNullOrBlank()) {
                    logger.warn("Không xác định được Firebase Project ID. Bỏ qua tạo phòng chat Firestore.")
                    return@launch
                }

                val accessToken = credentialsProvider.getAccessToken()
                if (accessToken.isNullOrBlank()) {
                    logger.warn("Không lấy được Google Access Token. Bỏ qua tạo phòng chat Firestore.")
                    return@launch
                }

                val nowIso = Instant.now().toString()
                val fields = buildJsonObject {
                    put("exchangeId", buildJsonObject { put("stringValue", exchange.id) })
                    put("senderId", buildJsonObject { put("stringValue", exchange.senderId) })
                    put("receiverId", buildJsonObject { put("stringValue", exchange.receiverId) })
                    put("senderName", buildJsonObject { put("stringValue", exchange.senderName?.ifBlank { "Đối tác" } ?: "Đối tác") })
                    put("receiverName", buildJsonObject { put("stringValue", exchange.receiverName?.ifBlank { "Đối tác" } ?: "Đối tác") })
                    exchange.senderAvatarUrl?.takeIf { it.isNotBlank() }?.let { url ->
                        put("senderAvatarUrl", buildJsonObject { put("stringValue", url) })
                    }
                    exchange.receiverAvatarUrl?.takeIf { it.isNotBlank() }?.let { url ->
                        put("receiverAvatarUrl", buildJsonObject { put("stringValue", url) })
                    }
                    put("skillOfferedName", buildJsonObject { put("stringValue", exchange.skillOfferedName) })
                    put("skillWantedName", buildJsonObject { put("stringValue", exchange.skillWantedName) })
                    put("status", buildJsonObject { put("stringValue", "ACCEPTED") })
                    put("participants", buildJsonObject {
                        put("arrayValue", buildJsonObject {
                            putJsonArray("values") {
                                add(buildJsonObject { put("stringValue", exchange.senderId) })
                                add(buildJsonObject { put("stringValue", exchange.receiverId) })
                            }
                        })
                    })
                    put("createdAt", buildJsonObject { put("timestampValue", nowIso) })
                    put("updatedAt", buildJsonObject { put("timestampValue", nowIso) })
                }
                val payload = buildJsonObject { put("fields", fields) }

                val url = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/chats/${exchange.id}"

                // Kiểm tra phòng chat đã tồn tại trên Firestore chưa để tránh ghi đè dữ liệu tin nhắn
                val getResponse = httpClient.get(url) {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                }
                if (getResponse.status == HttpStatusCode.OK) {
                    logger.info("Phòng chat Firestore đã tồn tại cho exchange: ${exchange.id}")
                    return@launch
                }

                val response = httpClient.patch(url) {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                    contentType(ContentType.Application.Json)
                    setBody(payload.toString())
                }

                if (response.status == HttpStatusCode.OK) {
                    logger.info("Đã khởi tạo phòng chat Firestore thành công cho exchange: ${exchange.id}")
                } else {
                    logger.error("Firestore REST trả mã lỗi ${response.status}: ${response.bodyAsText()}")
                }
            } catch (e: Exception) {
                logger.error("Lỗi khi tạo phòng chat Firestore cho exchange ${exchange.id}: ${e.message}")
            }
        }
    }

    override fun updateChatStatusAsync(exchangeId: String, status: String) {
        scope.launch {
            try {
                val projectId = credentialsProvider.getProjectId() ?: return@launch
                val accessToken = credentialsProvider.getAccessToken() ?: return@launch

                val nowIso = Instant.now().toString()
                val fields = buildJsonObject {
                    put("status", buildJsonObject { put("stringValue", status) })
                    put("updatedAt", buildJsonObject { put("timestampValue", nowIso) })
                }
                val payload = buildJsonObject { put("fields", fields) }

                val url = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/chats/$exchangeId?updateMask.fieldPaths=status&updateMask.fieldPaths=updatedAt"
                val response = httpClient.patch(url) {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                    contentType(ContentType.Application.Json)
                    setBody(payload.toString())
                }

                if (response.status == HttpStatusCode.OK) {
                    logger.info("Đã cập nhật trạng thái phòng chat Firestore sang $status cho exchange: $exchangeId")
                } else {
                    logger.warn("Cập nhật trạng thái Firestore thất bại (${response.status}): ${response.bodyAsText()}")
                }
            } catch (e: Exception) {
                logger.error("Lỗi khi cập nhật trạng thái Firestore cho exchange $exchangeId: ${e.message}")
            }
        }
    }
}
