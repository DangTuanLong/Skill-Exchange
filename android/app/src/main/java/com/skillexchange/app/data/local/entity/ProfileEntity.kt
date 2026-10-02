package com.skillexchange.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.skillexchange.app.domain.model.Profile

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val fullName: String,
    val bio: String?,
    val city: String?,
    val avatarUrl: String?,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Profile = Profile(
        id = id,
        userId = userId,
        fullName = fullName,
        bio = bio,
        city = city,
        avatarUrl = avatarUrl
    )

    companion object {
        fun fromDomain(profile: Profile): ProfileEntity = ProfileEntity(
            id = profile.id.ifEmpty { profile.userId },
            userId = profile.userId,
            fullName = profile.fullName,
            bio = profile.bio,
            city = profile.city,
            avatarUrl = profile.avatarUrl
        )
    }
}
