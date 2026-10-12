package com.skillexchange.api.services.friendship

import com.skillexchange.api.models.db.FriendshipsTable
import com.skillexchange.api.models.db.ProfilesTable
import com.skillexchange.api.models.friendship.FriendDto
import com.skillexchange.api.models.friendship.FriendshipDto
import com.skillexchange.api.models.friendship.FriendshipStatusDto
import com.skillexchange.api.plugins.ValidationException
import com.skillexchange.api.services.notification.NotificationService
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class FriendshipNotFoundException(message: String) : RuntimeException(message)
class FriendshipForbiddenException(message: String) : RuntimeException(message)
class FriendshipConflictException(message: String) : RuntimeException(message)

open class FriendshipService(
    private val notificationService: NotificationService? = null
) {
    private val logger = LoggerFactory.getLogger(FriendshipService::class.java)

    /**
     * Gửi lời mời kết bạn (POST /api/friendships).
     * Idempotent: Nếu đã là bạn hoặc đã gửi lời mời trước đó, trả về bản ghi hiện tại.
     * Nếu đối phương đã gửi cho caller, tự động chấp nhận.
     */
    open fun sendRequest(callerId: String, targetFriendId: String): FriendshipDto {
        if (callerId == targetFriendId) {
            throw ValidationException("Không thể gửi lời mời kết bạn cho chính mình.")
        }

        try {
            UUID.fromString(targetFriendId)
        } catch (e: Exception) {
            throw ValidationException("ID người bạn không hợp lệ.")
        }

        var shouldNotify = false
        var notifyType = "FRIEND_REQUEST"

        val friendship = transaction {
            // Kiểm tra profile người nhận có tồn tại không
            val targetExists = ProfilesTable.selectAll().where { ProfilesTable.userId eq targetFriendId }.count() > 0
            if (!targetExists) {
                throw FriendshipNotFoundException("Không tìm thấy người dùng với ID này.")
            }

            // Tìm quan hệ hiện tại giữa 2 người (bất kể chiều)
            val existing = FriendshipsTable.selectAll()
                .where {
                    ((FriendshipsTable.userId eq callerId) and (FriendshipsTable.friendId eq targetFriendId)) or
                    ((FriendshipsTable.userId eq targetFriendId) and (FriendshipsTable.friendId eq callerId))
                }
                .firstOrNull()

            if (existing != null) {
                val currentStatus = existing[FriendshipsTable.status]
                val senderId = existing[FriendshipsTable.userId]
                val fId = existing[FriendshipsTable.id]

                when (currentStatus) {
                    "ACCEPTED" -> {
                        // Đã là bạn bè -> Idempotent
                        return@transaction existing.toDto()
                    }
                    "PENDING" -> {
                        if (senderId == callerId) {
                            // Caller đã gửi trước đó -> Idempotent
                            return@transaction existing.toDto()
                        } else {
                            // Target đã gửi cho caller, nay caller gửi lại -> Tự động chấp nhận!
                            val now = LocalDateTime.now()
                            FriendshipsTable.update({ FriendshipsTable.id eq fId }) {
                                it[status] = "ACCEPTED"
                                it[updatedAt] = now
                            }
                            shouldNotify = true
                            notifyType = "FRIEND_ACCEPTED"
                            return@transaction FriendshipsTable.selectAll().where { FriendshipsTable.id eq fId }.first().toDto()
                        }
                    }
                    else -> {
                        // REJECTED -> Cập nhật lại thành PENDING với caller là người gửi
                        val now = LocalDateTime.now()
                        FriendshipsTable.update({ FriendshipsTable.id eq fId }) {
                            it[userId] = callerId
                            it[friendId] = targetFriendId
                            it[status] = "PENDING"
                            it[updatedAt] = now
                        }
                        shouldNotify = true
                        notifyType = "FRIEND_REQUEST"
                        return@transaction FriendshipsTable.selectAll().where { FriendshipsTable.id eq fId }.first().toDto()
                    }
                }
            }

            // Tạo mới lời mời
            val newId = UUID.randomUUID()
            val now = LocalDateTime.now()
            FriendshipsTable.insert {
                it[id] = newId
                it[userId] = callerId
                it[friendId] = targetFriendId
                it[status] = "PENDING"
                it[createdAt] = now
                it[updatedAt] = now
            }
            shouldNotify = true
            notifyType = "FRIEND_REQUEST"

            FriendshipsTable.selectAll().where { FriendshipsTable.id eq newId }.first().toDto()
        }

        // Gửi thông báo bất đồng bộ qua NotificationService
        if (shouldNotify && notificationService != null) {
            try {
                val callerProfile = transaction {
                    ProfilesTable.selectAll().where { ProfilesTable.userId eq callerId }.firstOrNull()
                }
                val callerName = callerProfile?.get(ProfilesTable.fullName) ?: "Một người dùng"
                if (notifyType == "FRIEND_REQUEST") {
                    notificationService.createNotification(
                        userId = targetFriendId,
                        type = "FRIEND_REQUEST",
                        title = "Lời mời kết bạn mới",
                        body = "$callerName đã gửi lời mời kết bạn cho bạn.",
                        entityId = friendship.id
                    )
                } else if (notifyType == "FRIEND_ACCEPTED") {
                    notificationService.createNotification(
                        userId = targetFriendId,
                        type = "FRIEND_ACCEPTED",
                        title = "Lời mời kết bạn được chấp nhận",
                        body = "$callerName đã chấp nhận lời mời kết bạn của bạn.",
                        entityId = friendship.id
                    )
                }
            } catch (e: Exception) {
                logger.warn("Không thể gửi thông báo kết bạn: ${e.message}")
            }
        }

        return friendship
    }

    /**
     * Chấp nhận lời mời kết bạn (PUT /api/friendships/{id}/accept).
     * Chỉ người nhận (friend_id) mới có quyền chấp nhận.
     * Idempotent nếu đã ACCEPTED.
     */
    open fun acceptRequest(callerId: String, friendshipId: String): FriendshipDto {
        val fUuid = try {
            UUID.fromString(friendshipId)
        } catch (e: Exception) {
            throw ValidationException("ID lời mời kết bạn không hợp lệ.")
        }

        var shouldNotify = false
        var senderUserId = ""

        val friendship = transaction {
            val row = FriendshipsTable.selectAll().where { FriendshipsTable.id eq fUuid }.firstOrNull()
                ?: throw FriendshipNotFoundException("Không tìm thấy lời mời kết bạn.")

            val currentStatus = row[FriendshipsTable.status]
            val senderId = row[FriendshipsTable.userId]
            val receiverId = row[FriendshipsTable.friendId]
            senderUserId = senderId

            // Kiểm tra quyền: Chỉ người nhận mới được chấp nhận
            if (callerId == senderId) {
                throw FriendshipForbiddenException("Chỉ người nhận mới có quyền chấp nhận lời mời kết bạn.")
            }
            if (callerId != receiverId) {
                throw FriendshipForbiddenException("Bạn không có quyền thực hiện thao tác này.")
            }

            if (currentStatus == "ACCEPTED") {
                // Đã chấp nhận rồi -> Idempotent
                return@transaction row.toDto()
            }

            if (currentStatus == "REJECTED") {
                throw FriendshipConflictException("Lời mời kết bạn này đã bị từ chối trước đó.")
            }

            val now = LocalDateTime.now()
            FriendshipsTable.update({ FriendshipsTable.id eq fUuid }) {
                it[status] = "ACCEPTED"
                it[updatedAt] = now
            }
            shouldNotify = true

            FriendshipsTable.selectAll().where { FriendshipsTable.id eq fUuid }.first().toDto()
        }

        if (shouldNotify && notificationService != null) {
            try {
                val receiverProfile = transaction {
                    ProfilesTable.selectAll().where { ProfilesTable.userId eq callerId }.firstOrNull()
                }
                val receiverName = receiverProfile?.get(ProfilesTable.fullName) ?: "Một người dùng"
                notificationService.createNotification(
                    userId = senderUserId,
                    type = "FRIEND_ACCEPTED",
                    title = "Lời mời kết bạn được chấp nhận",
                    body = "$receiverName đã chấp nhận lời mời kết bạn của bạn.",
                    entityId = friendship.id
                )
            } catch (e: Exception) {
                logger.warn("Không thể gửi thông báo FRIEND_ACCEPTED: ${e.message}")
            }
        }

        return friendship
    }

    /**
     * Từ chối lời mời kết bạn (PUT /api/friendships/{id}/reject).
     * Chỉ người nhận mới có quyền từ chối.
     * Idempotent nếu đã REJECTED.
     */
    open fun rejectRequest(callerId: String, friendshipId: String): FriendshipDto = transaction {
        val fUuid = try {
            UUID.fromString(friendshipId)
        } catch (e: Exception) {
            throw ValidationException("ID lời mời kết bạn không hợp lệ.")
        }

        val row = FriendshipsTable.selectAll().where { FriendshipsTable.id eq fUuid }.firstOrNull()
            ?: throw FriendshipNotFoundException("Không tìm thấy lời mời kết bạn.")

        val currentStatus = row[FriendshipsTable.status]
        val senderId = row[FriendshipsTable.userId]
        val receiverId = row[FriendshipsTable.friendId]

        if (callerId == senderId) {
            throw FriendshipForbiddenException("Chỉ người nhận mới có quyền từ chối lời mời kết bạn.")
        }
        if (callerId != receiverId) {
            throw FriendshipForbiddenException("Bạn không có quyền thực hiện thao tác này.")
        }

        if (currentStatus == "REJECTED") {
            return@transaction row.toDto()
        }

        if (currentStatus == "ACCEPTED") {
            throw FriendshipConflictException("Không thể từ chối quan hệ bạn bè đã được chấp nhận. Vui lòng chọn xóa bạn.")
        }

        val now = LocalDateTime.now()
        FriendshipsTable.update({ FriendshipsTable.id eq fUuid }) {
            it[status] = "REJECTED"
            it[updatedAt] = now
        }

        FriendshipsTable.selectAll().where { FriendshipsTable.id eq fUuid }.first().toDto()
    }

    /**
     * Xóa bạn bè hoặc hủy lời mời kết bạn (DELETE /api/friendships/{id}).
     * Cả 2 bên đều có quyền xóa.
     * Idempotent nếu bản ghi không tồn tại.
     */
    open fun removeFriendship(callerId: String, friendshipId: String): Boolean = transaction {
        val fUuid = try {
            UUID.fromString(friendshipId)
        } catch (e: Exception) {
            throw ValidationException("ID lời mời kết bạn không hợp lệ.")
        }

        val row = FriendshipsTable.selectAll().where { FriendshipsTable.id eq fUuid }.firstOrNull()
            ?: return@transaction true // Idempotent nếu đã xóa

        val senderId = row[FriendshipsTable.userId]
        val receiverId = row[FriendshipsTable.friendId]

        if (callerId != senderId && callerId != receiverId) {
            throw FriendshipForbiddenException("Bạn không có quyền xóa quan hệ bạn bè này.")
        }

        FriendshipsTable.deleteWhere { FriendshipsTable.id eq fUuid } > 0
    }

    /**
     * Lấy danh sách bạn bè đã chấp nhận (GET /api/friendships/me).
     */
    open fun getFriends(callerId: String): List<FriendDto> = transaction {
        val rows = FriendshipsTable.selectAll()
            .where {
                ((FriendshipsTable.userId eq callerId) or (FriendshipsTable.friendId eq callerId)) and
                (FriendshipsTable.status eq "ACCEPTED")
            }
            .orderBy(FriendshipsTable.updatedAt to org.jetbrains.exposed.sql.SortOrder.DESC)
            .toList()

        rows.map { row ->
            val otherUserId = if (row[FriendshipsTable.userId] == callerId) {
                row[FriendshipsTable.friendId]
            } else {
                row[FriendshipsTable.userId]
            }

            val profile = ProfilesTable.selectAll().where { ProfilesTable.userId eq otherUserId }.firstOrNull()
            FriendDto(
                friendshipId = row[FriendshipsTable.id].toString(),
                userId = otherUserId,
                fullName = profile?.get(ProfilesTable.fullName) ?: "Người dùng",
                avatarUrl = profile?.get(ProfilesTable.avatarUrl),
                city = profile?.get(ProfilesTable.city),
                bio = profile?.get(ProfilesTable.bio),
                status = "ACCEPTED",
                createdAt = row[FriendshipsTable.createdAt].format(DateTimeFormatter.ISO_DATE_TIME)
            )
        }
    }

    /**
     * Lấy danh sách lời mời đang chờ gửi tới tôi (GET /api/friendships/pending).
     */
    open fun getPendingRequests(callerId: String): List<FriendDto> = transaction {
        val rows = FriendshipsTable.selectAll()
            .where {
                (FriendshipsTable.friendId eq callerId) and (FriendshipsTable.status eq "PENDING")
            }
            .orderBy(FriendshipsTable.createdAt to org.jetbrains.exposed.sql.SortOrder.DESC)
            .toList()

        rows.map { row ->
            val senderId = row[FriendshipsTable.userId]
            val profile = ProfilesTable.selectAll().where { ProfilesTable.userId eq senderId }.firstOrNull()
            FriendDto(
                friendshipId = row[FriendshipsTable.id].toString(),
                userId = senderId,
                fullName = profile?.get(ProfilesTable.fullName) ?: "Người dùng",
                avatarUrl = profile?.get(ProfilesTable.avatarUrl),
                city = profile?.get(ProfilesTable.city),
                bio = profile?.get(ProfilesTable.bio),
                status = "PENDING",
                createdAt = row[FriendshipsTable.createdAt].format(DateTimeFormatter.ISO_DATE_TIME)
            )
        }
    }

    /**
     * Kiểm tra trạng thái quan hệ với 1 user cụ thể (GET /api/friendships/status/{userId}).
     */
    open fun getFriendshipStatus(callerId: String, targetUserId: String): FriendshipStatusDto = transaction {
        if (callerId == targetUserId) {
            return@transaction FriendshipStatusDto(status = "NONE", friendshipId = null)
        }

        val row = FriendshipsTable.selectAll()
            .where {
                ((FriendshipsTable.userId eq callerId) and (FriendshipsTable.friendId eq targetUserId)) or
                ((FriendshipsTable.userId eq targetUserId) and (FriendshipsTable.friendId eq callerId))
            }
            .firstOrNull()
            ?: return@transaction FriendshipStatusDto(status = "NONE", friendshipId = null)

        val status = row[FriendshipsTable.status]
        val senderId = row[FriendshipsTable.userId]
        val fId = row[FriendshipsTable.id].toString()

        val resolvedStatus = when (status) {
            "ACCEPTED" -> "ACCEPTED"
            "PENDING" -> if (senderId == callerId) "PENDING_SENT" else "PENDING_RECEIVED"
            "REJECTED" -> if (senderId == callerId) "REJECTED" else "NONE"
            else -> "NONE"
        }

        FriendshipStatusDto(status = resolvedStatus, friendshipId = fId)
    }

    private fun ResultRow.toDto(): FriendshipDto = FriendshipDto(
        id = this[FriendshipsTable.id].toString(),
        userId = this[FriendshipsTable.userId],
        friendId = this[FriendshipsTable.friendId],
        status = this[FriendshipsTable.status],
        createdAt = this[FriendshipsTable.createdAt].format(DateTimeFormatter.ISO_DATE_TIME),
        updatedAt = this[FriendshipsTable.updatedAt].format(DateTimeFormatter.ISO_DATE_TIME)
    )
}
