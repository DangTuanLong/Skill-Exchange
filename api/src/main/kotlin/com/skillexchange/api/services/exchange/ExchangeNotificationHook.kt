package com.skillexchange.api.services.exchange

import com.skillexchange.api.models.exchange.ExchangeRequestDto
import com.skillexchange.api.services.device.DeviceService
import com.skillexchange.api.services.notification.FcmService
import org.slf4j.LoggerFactory

/**
 * Interface điểm móc (Hook) cho các nghiệp vụ tiếp theo:
 * - TASK-023: Gửi Push Notification qua Firebase Cloud Messaging (FCM).
 * - TASK-030: Khởi tạo phòng chat trên Firebase Firestore khi yêu cầu được chấp nhận.
 */
interface IExchangeNotificationHook {
    fun onRequestCreated(exchange: ExchangeRequestDto) {}
    fun onRequestAccepted(exchange: ExchangeRequestDto) {}
    fun onRequestRejected(exchange: ExchangeRequestDto) {}
    fun onRequestCancelled(exchange: ExchangeRequestDto, cancelledBy: String) {}
    fun onRequestCompleted(exchange: ExchangeRequestDto) {}
}

/**
 * Implementation mặc định không làm gì (No-op) dùng trong test hoặc fallback.
 */
class NoOpExchangeNotificationHook : IExchangeNotificationHook

/**
 * Implementation tích hợp FCM gửi Push Notification theo BUSINESS_RULES.md §6:
 * Khi request mới được tạo -> "{Tên} muốn trao đổi {skill}"
 * Deep link payload mang duy nhất `type` và `entityId`.
 */
class FcmExchangeNotificationHook(
    private val fcmService: FcmService,
    private val deviceService: DeviceService,
    private val firestoreChatService: com.skillexchange.api.services.chat.IFirestoreChatService? = null
) : IExchangeNotificationHook {

    private val logger = LoggerFactory.getLogger(FcmExchangeNotificationHook::class.java)

    override fun onRequestCreated(exchange: ExchangeRequestDto) {
        try {
            val receiverId = exchange.receiverId
            val tokens = deviceService.getTokensForUser(receiverId)
            if (tokens.isEmpty()) {
                logger.info("Không có FCM token nào cho receiver: $receiverId")
                return
            }

            val senderName = exchange.senderName?.ifBlank { "Ai đó" } ?: "Ai đó"
            val skillName = exchange.skillOfferedName
            val title = "Yêu cầu trao đổi mới"
            val body = "$senderName muốn trao đổi $skillName"
            val data = mapOf(
                "type" to "NEW_REQUEST",
                "entityId" to exchange.id
            )

            tokens.forEach { token ->
                fcmService.sendPushAsync(
                    targetToken = token,
                    title = title,
                    body = body,
                    data = data
                )
            }
        } catch (e: Throwable) {
            // Requirement 4: Lỗi gửi push chỉ ghi log, không làm thất bại tạo request
            logger.error("Lỗi khi kích hoạt hook gửi FCM cho exchange ${exchange.id}: ${e.message}")
        }
    }

    override fun onRequestAccepted(exchange: ExchangeRequestDto) {
        try {
            // TASK-030: Tạo phòng chat trên Firestore khi request chuyển ACCEPTED (idempotent: exchange.id)
            firestoreChatService?.createChatRoomAsync(exchange)
        } catch (e: Throwable) {
            logger.error("Lỗi khi kích hoạt hook tạo phòng chat Firestore cho exchange ${exchange.id}: ${e.message}")
        }
    }

    override fun onRequestCancelled(exchange: ExchangeRequestDto, cancelledBy: String) {
        try {
            // TASK-030: Cập nhật status phòng chat sang CANCELLED
            firestoreChatService?.updateChatStatusAsync(exchange.id, "CANCELLED")
        } catch (e: Throwable) {
            logger.error("Lỗi khi kích hoạt hook cập nhật CANCELLED phòng chat Firestore cho exchange ${exchange.id}: ${e.message}")
        }
    }

    override fun onRequestCompleted(exchange: ExchangeRequestDto) {
        try {
            // TASK-030: Cập nhật status phòng chat sang COMPLETED
            firestoreChatService?.updateChatStatusAsync(exchange.id, "COMPLETED")
        } catch (e: Throwable) {
            logger.error("Lỗi khi kích hoạt hook cập nhật COMPLETED phòng chat Firestore cho exchange ${exchange.id}: ${e.message}")
        }
    }
}

