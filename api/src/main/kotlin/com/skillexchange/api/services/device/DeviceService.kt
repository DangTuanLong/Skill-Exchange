package com.skillexchange.api.services.device

import com.skillexchange.api.models.db.FcmTokensTable
import com.skillexchange.api.plugins.ValidationException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

open class DeviceService {

    /**
     * Đăng ký hoặc cập nhật FCM token của người dùng (upsert).
     */
    open fun registerToken(userId: String, token: String, deviceInfo: String?): Unit = transaction {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) {
            throw ValidationException("FCM token không được để trống")
        }
        val cleanDeviceInfo = deviceInfo?.trim()?.takeIf { it.isNotBlank() }

        val existing = FcmTokensTable.selectAll()
            .where { FcmTokensTable.token eq cleanToken }
            .firstOrNull()

        val now = LocalDateTime.now()

        if (existing != null) {
            FcmTokensTable.update({ FcmTokensTable.token eq cleanToken }) {
                it[this.userId] = userId
                it[this.deviceInfo] = cleanDeviceInfo
                it[updatedAt] = now
            }
        } else {
            FcmTokensTable.insert {
                it[id] = UUID.randomUUID()
                it[this.userId] = userId
                it[this.token] = cleanToken
                it[this.deviceInfo] = cleanDeviceInfo
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
    }

    /**
     * Xóa token của người dùng (khi đăng xuất).
     */
    open fun deleteToken(userId: String, token: String): Boolean = transaction {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) return@transaction false

        val rowsDeleted = FcmTokensTable.deleteWhere {
            (FcmTokensTable.userId eq userId) and (FcmTokensTable.token eq cleanToken)
        }
        rowsDeleted > 0
    }

    /**
     * Xóa token trực tiếp khi FCM báo lỗi UNREGISTERED / 404 (stale token).
     */
    open fun deleteTokenDirect(token: String): Boolean = transaction {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) return@transaction false

        val rowsDeleted = FcmTokensTable.deleteWhere {
            FcmTokensTable.token eq cleanToken
        }
        rowsDeleted > 0
    }

    /**
     * Lấy danh sách FCM token của người dùng để gửi push notification.
     */
    open fun getTokensForUser(userId: String): List<String> = transaction {
        FcmTokensTable.selectAll()
            .where { FcmTokensTable.userId eq userId }
            .map { it[FcmTokensTable.token] }
    }
}
