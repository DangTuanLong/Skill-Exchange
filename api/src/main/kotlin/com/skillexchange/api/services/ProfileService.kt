package com.skillexchange.api.services

import com.skillexchange.api.models.db.ProfilesTable
import com.skillexchange.api.models.profile.ProfileDto
import com.skillexchange.api.models.profile.UpdateProfileRequest
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class ProfileService {

    fun getProfile(userId: String): ProfileDto? = transaction {
        ProfilesTable.selectAll()
            .where { ProfilesTable.userId eq userId }
            .firstOrNull()
            ?.toDto()
    }

    fun upsertProfile(userId: String, req: UpdateProfileRequest): ProfileDto = transaction {
        val existing = ProfilesTable.selectAll()
            .where { ProfilesTable.userId eq userId }
            .firstOrNull()

        if (existing == null) {
            // INSERT
            ProfilesTable.insert {
                it[ProfilesTable.userId]   = userId
                it[fullName]               = req.fullName
                it[bio]                    = req.bio
                it[city]                   = req.city
                it[avatarUrl]              = req.avatarUrl
                it[availability]           = req.availability ?: emptyList()
                it[updatedAt]              = LocalDateTime.now()
            }
        } else {
            // UPDATE
            ProfilesTable.update({ ProfilesTable.userId eq userId }) {
                it[fullName]   = req.fullName
                it[bio]        = req.bio
                it[city]       = req.city
                it[avatarUrl]  = req.avatarUrl
                if (req.availability != null) {
                    it[availability] = req.availability
                }
                it[updatedAt]  = LocalDateTime.now()
            }
        }

        ProfilesTable.selectAll()
            .where { ProfilesTable.userId eq userId }
            .first().toDto()
    }

    fun updateAvatarUrl(userId: String, newAvatarUrl: String): String? = transaction {
        val existing = ProfilesTable.selectAll()
            .where { ProfilesTable.userId eq userId }
            .firstOrNull()

        val oldAvatarUrl = existing?.get(ProfilesTable.avatarUrl)

        if (existing == null) {
            ProfilesTable.insert {
                it[ProfilesTable.userId] = userId
                it[fullName]             = "User"
                it[avatarUrl]            = newAvatarUrl
                it[updatedAt]            = LocalDateTime.now()
            }
        } else {
            ProfilesTable.update({ ProfilesTable.userId eq userId }) {
                it[avatarUrl] = newAvatarUrl
                it[updatedAt] = LocalDateTime.now()
            }
        }
        oldAvatarUrl
    }


    private fun ResultRow.toDto() = ProfileDto(
        id           = this[ProfilesTable.id].toString(),
        userId       = this[ProfilesTable.userId],
        fullName     = this[ProfilesTable.fullName],
        bio          = this[ProfilesTable.bio],
        city         = this[ProfilesTable.city],
        avatarUrl    = this[ProfilesTable.avatarUrl],
        availability = this[ProfilesTable.availability] ?: emptyList(),
        updatedAt    = this[ProfilesTable.updatedAt].toString()
    )

}
