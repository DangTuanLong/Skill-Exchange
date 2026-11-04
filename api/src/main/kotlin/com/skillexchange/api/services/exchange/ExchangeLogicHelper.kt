package com.skillexchange.api.services.exchange

import com.skillexchange.api.models.ValidationConstants
import com.skillexchange.api.models.exchange.CreateExchangeRequest
import com.skillexchange.api.models.exchange.ExchangeStatus
import com.skillexchange.api.models.exchange.ValidPairsResponse
import com.skillexchange.api.models.exchange.ValidSkillPairDto
import com.skillexchange.api.plugins.ValidationException
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * Các hàm logic thuần túy (không I/O, không CSDL) của phân hệ Trao đổi kỹ năng (Exchange).
 * Cho phép kiểm thử đơn vị độc lập 100% mà không cần kết nối cơ sở dữ liệu thật.
 */
object ExchangeLogicHelper {

    val ALLOWED_DURATIONS = listOf(30, 60, 90, 120)
    val ALLOWED_MODES = listOf("ONLINE", "IN_PERSON", "UNDECIDED")

    /**
     * Kiểm tra hợp lệ các trường dữ liệu đầu vào của yêu cầu tạo trao đổi.
     */
    fun validateCreateExchangeInput(
        senderId: String,
        req: CreateExchangeRequest,
        now: LocalDateTime = LocalDateTime.now()
    ) {
        if (senderId == req.receiverId) {
            throw ValidationException("Không thể gửi yêu cầu trao đổi cho chính mình")
        }

        if (req.message != null && req.message.length > ValidationConstants.MAX_EXCHANGE_MESSAGE_LENGTH) {
            throw ValidationException("Lời nhắn không được vượt quá ${ValidationConstants.MAX_EXCHANGE_MESSAGE_LENGTH} ký tự")
        }

        if (req.durationMinutes !in ALLOWED_DURATIONS) {
            throw ValidationException("Thời lượng trao đổi phải là 30, 60, 90 hoặc 120 phút")
        }

        val upperMode = req.meetingMode.uppercase().trim()
        if (upperMode !in ALLOWED_MODES) {
            throw ValidationException("Chế độ gặp gỡ không hợp lệ (phải là ONLINE, IN_PERSON hoặc UNDECIDED)")
        }

        val scheduledDateTime = parseIsoDateTime(req.scheduledAt)
        if (!scheduledDateTime.isAfter(now)) {
            throw ValidationException("Thời gian hẹn phải ở tương lai")
        }
    }

    /**
     * Chuyển đổi chuỗi ISO-8601 sang LocalDateTime an toàn.
     */
    fun parseIsoDateTime(str: String): LocalDateTime {
        return try {
            LocalDateTime.parse(str)
        } catch (_: Exception) {
            try {
                java.time.OffsetDateTime.parse(str).toLocalDateTime()
            } catch (_: Exception) {
                try {
                    java.time.Instant.parse(str).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDateTime()
                } catch (_: Exception) {
                    throw ValidationException("Định dạng scheduled_at không hợp lệ. Vui lòng dùng chuẩn ISO-8601 (ví dụ: 2026-10-15T19:00:00)")
                }
            }
        }
    }

    /**
     * Kiểm tra điều kiện trình độ kỹ năng 2 chiều (BUSINESS_RULES.md §3).
     * Sender dạy skillOffered: senderHave >= receiverWant.
     * Receiver dạy skillWanted: receiverHave >= senderWant.
     */
    fun validateProficiencyMatch(
        senderHaveLevel: Int?,
        receiverWantLevel: Int?,
        receiverHaveLevel: Int?,
        senderWantLevel: Int?
    ) {
        if (senderHaveLevel == null) {
            throw ValidationException("Người gửi không có kỹ năng dạy được chọn (skill_offered_id)")
        }
        if (receiverWantLevel == null) {
            throw ValidationException("Người nhận không có nhu cầu học kỹ năng bạn đề xuất dạy")
        }
        if (senderHaveLevel < receiverWantLevel) {
            throw ValidationException(
                "Trình độ kỹ năng dạy của bạn ($senderHaveLevel) chưa đạt mức mục tiêu người nhận cần học ($receiverWantLevel)"
            )
        }

        if (receiverHaveLevel == null) {
            throw ValidationException("Người nhận không có kỹ năng bạn muốn học (skill_wanted_id)")
        }
        if (senderWantLevel == null) {
            throw ValidationException("Người gửi không có kỹ năng muốn học trong hồ sơ của mình")
        }
        if (receiverHaveLevel < senderWantLevel) {
            throw ValidationException(
                "Trình độ kỹ năng của người nhận ($receiverHaveLevel) chưa đạt mức mục tiêu bạn muốn học ($senderWantLevel)"
            )
        }
    }

    /**
     * Kiểm tra chặn trùng cặp kỹ năng đối xứng đang mở (DEC-028).
     */
    fun checkSymmetricPairActive(
        senderId: String,
        receiverId: String,
        skillOfferedId: Int,
        skillWantedId: Int,
        existingRequests: List<ExistingExchangeRequestSummary>
    ): Boolean {
        return existingRequests.any { req ->
            req.status in listOf(ExchangeStatus.PENDING.name, ExchangeStatus.ACCEPTED.name) &&
            (
                (req.senderId == senderId && req.receiverId == receiverId &&
                 req.skillOfferedId == skillOfferedId && req.skillWantedId == skillWantedId) ||
                (req.senderId == receiverId && req.receiverId == senderId &&
                 req.skillOfferedId == skillWantedId && req.skillWantedId == skillOfferedId)
            )
        }
    }

    /**
     * Kiểm tra giới hạn 3 yêu cầu PENDING đồng thời giữa 2 người dùng (DEC-028).
     */
    fun checkPendingLimitReached(
        senderId: String,
        receiverId: String,
        existingRequests: List<ExistingExchangeRequestSummary>,
        maxLimit: Int = 3
    ): Boolean {
        val count = existingRequests.count { req ->
            req.status == ExchangeStatus.PENDING.name &&
            (
                (req.senderId == senderId && req.receiverId == receiverId) ||
                (req.senderId == receiverId && req.receiverId == senderId)
            )
        }
        return count >= maxLimit
    }

    /**
     * Tính toán danh sách cặp kỹ năng hợp lệ còn khả dụng giữa caller và receiver (DEC-028).
     */
    fun computeValidPairs(
        callerId: String,
        receiverId: String,
        callerHaveSkills: List<Triple<Int, String, Int>>, // id, name, level
        receiverWantSkills: Map<Int, Pair<String, Int>>,   // id -> (name, level)
        receiverHaveSkills: List<Triple<Int, String, Int>>,
        callerWantSkills: Map<Int, Pair<String, Int>>,
        existingRequests: List<ExistingExchangeRequestSummary>,
        now: LocalDateTime = LocalDateTime.now(),
        cooldownDays: Long = 14L
    ): ValidPairsResponse {
        if (callerId == receiverId) {
            return ValidPairsResponse(emptyList(), false)
        }

        // 1. Tìm các kỹ năng caller dạy được receiver
        val validTeachSkills = callerHaveSkills.filter { (id, _, level) ->
            val want = receiverWantSkills[id]
            want != null && level >= want.second
        }

        // 2. Tìm các kỹ năng receiver dạy được caller
        val validLearnSkills = receiverHaveSkills.filter { (id, _, level) ->
            val want = callerWantSkills[id]
            want != null && level >= want.second
        }

        // 3. Kiểm tra xem có exchange bị từ chối/hủy gần đây không
        val cooldownCutoff = now.minusDays(cooldownDays)
        val recentlyRejected = existingRequests.any { req ->
            req.status in listOf(ExchangeStatus.REJECTED.name, ExchangeStatus.CANCELLED.name) &&
            (req.resolvedAt ?: req.updatedAt ?: req.createdAt) > cooldownCutoff
        }

        // 4. Tổ hợp các cặp kỹ năng hợp lệ
        val pairs = mutableListOf<ValidSkillPairDto>()
        for (teach in validTeachSkills) {
            for (learn in validLearnSkills) {
                val teachId = teach.first
                val teachName = teach.second
                val learnId = learn.first
                val learnName = learn.second

                val isPairActive = existingRequests.any { req ->
                    req.status in listOf(ExchangeStatus.PENDING.name, ExchangeStatus.ACCEPTED.name) &&
                    (
                        (req.senderId == callerId && req.receiverId == receiverId &&
                         req.skillOfferedId == teachId && req.skillWantedId == learnId) ||
                        (req.senderId == receiverId && req.receiverId == callerId &&
                         req.skillOfferedId == learnId && req.skillWantedId == teachId)
                    )
                }

                val completedCount = existingRequests.count { req ->
                    req.status == ExchangeStatus.COMPLETED.name &&
                    (
                        (req.senderId == callerId && req.receiverId == receiverId &&
                         req.skillOfferedId == teachId && req.skillWantedId == learnId) ||
                        (req.senderId == receiverId && req.receiverId == callerId &&
                         req.skillOfferedId == learnId && req.skillWantedId == teachId)
                    )
                }

                pairs.add(
                    ValidSkillPairDto(
                        skillOfferedId = teachId,
                        skillOfferedName = teachName,
                        skillWantedId = learnId,
                        skillWantedName = learnName,
                        isActive = isPairActive,
                        completedCount = completedCount
                    )
                )
            }
        }

        val hasActive = existingRequests.any { req ->
            req.status in listOf(ExchangeStatus.PENDING.name, ExchangeStatus.ACCEPTED.name)
        }
        val completedCount = existingRequests.count { req ->
            req.status == ExchangeStatus.COMPLETED.name
        }
        val relStatus = if (hasActive) "ACTIVE" else if (completedCount > 0) "COMPLETED" else null

        return ValidPairsResponse(
            pairs = pairs,
            recentlyRejected = recentlyRejected,
            relationshipStatus = relStatus,
            completedExchangeCount = completedCount
        )
    }
}

/**
 * Model tóm tắt request dùng cho các hàm logic thuần túy không phụ thuộc Exposed ResultRow.
 */
data class ExistingExchangeRequestSummary(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val receiverId: String,
    val skillOfferedId: Int,
    val skillWantedId: Int,
    val status: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime? = null,
    val resolvedAt: LocalDateTime? = null
)

