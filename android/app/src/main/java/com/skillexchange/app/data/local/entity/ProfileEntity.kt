package com.skillexchange.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.skillexchange.app.data.remote.profile.AvailabilityWindowDto
import com.skillexchange.app.domain.model.AvailabilityWindow
import com.skillexchange.app.domain.model.Profile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val fullName: String,
    val bio: String?,
    val city: String?,
    val avatarUrl: String?,
    val availabilityJson: String = "[]",
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Profile {
        val parsedWindows: List<AvailabilityWindow> = try {
            if (availabilityJson.isBlank() || availabilityJson == "[]") {
                emptyList()
            } else {
                Json.decodeFromString<List<AvailabilityWindowDto>>(availabilityJson).map {
                    AvailabilityWindow(day = it.day, from = it.from, to = it.to)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
        return Profile(
            id = id,
            userId = userId,
            fullName = fullName,
            bio = bio,
            city = city,
            avatarUrl = avatarUrl,
            availability = parsedWindows
        )
    }

    companion object {
        fun fromDomain(profile: Profile): ProfileEntity {
            val jsonStr = try {
                val dtos = profile.availability.map {
                    AvailabilityWindowDto(day = it.day, from = it.from, to = it.to)
                }
                Json.encodeToString(dtos)
            } catch (_: Exception) {
                "[]"
            }
            return ProfileEntity(
                id = profile.id.ifEmpty { profile.userId },
                userId = profile.userId,
                fullName = profile.fullName,
                bio = profile.bio,
                city = profile.city,
                avatarUrl = profile.avatarUrl,
                availabilityJson = jsonStr
            )
        }
    }
}

