package com.skillexchange.api.models.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AvailabilityWindowDto(
    val day: String,
    val from: String,
    val to: String
)

@Serializable
data class ProfileDto(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String,
    val bio: String? = null,
    val city: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val availability: List<AvailabilityWindowDto> = emptyList(),
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class UpdateProfileRequest(
    @SerialName("full_name") val fullName: String,
    val bio: String? = null,
    val city: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val availability: List<AvailabilityWindowDto>? = null
)

object AvailabilityValidator {
    val VALID_DAYS = setOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    private val TIME_REGEX = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

    fun validate(windows: List<AvailabilityWindowDto>): String? {
        for (w in windows) {
            val dayUpper = w.day.uppercase()
            if (dayUpper !in VALID_DAYS) {
                return "Ngày '${w.day}' không hợp lệ (phải thuộc MON..SUN)"
            }
            if (!TIME_REGEX.matches(w.from) || !TIME_REGEX.matches(w.to)) {
                return "Định dạng giờ không hợp lệ (phải là HH:mm từ 00:00 đến 23:59)"
            }
            if (w.from >= w.to) {
                return "Giờ bắt đầu (${w.from}) phải sớm hơn giờ kết thúc (${w.to})"
            }
        }

        val grouped = windows.groupBy { it.day.uppercase() }
        for ((day, dayWindows) in grouped) {
            val sorted = dayWindows.sortedBy { it.from }
            for (i in 0 until sorted.size - 1) {
                val current = sorted[i]
                val next = sorted[i + 1]
                if (next.from < current.to) {
                    return "Khung giờ ${current.from}-${current.to} và ${next.from}-${next.to} trong ngày $day bị chồng nhau"
                }
            }
        }
        return null
    }
}

@Serializable
data class AvatarUploadResponse(
    @SerialName("avatar_url") val avatarUrl: String
)

data class DetectedImageType(val extension: String, val mimeType: String)

object ImageTypeDetector {
    fun detect(bytes: ByteArray): DetectedImageType? {
        if (bytes.size < 12) return null

        // JPEG: FF D8 FF
        if ((bytes[0].toInt() and 0xFF) == 0xFF &&
            (bytes[1].toInt() and 0xFF) == 0xD8 &&
            (bytes[2].toInt() and 0xFF) == 0xFF
        ) {
            return DetectedImageType("jpg", "image/jpeg")
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (bytes.size >= 8 &&
            (bytes[0].toInt() and 0xFF) == 0x89 &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte() &&
            bytes[4] == 0x0D.toByte() &&
            bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() &&
            bytes[7] == 0x0A.toByte()
        ) {
            return DetectedImageType("png", "image/png")
        }

        // WebP: RIFF at 0..3 and WEBP at 8..11
        if (bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() &&
            bytes[11] == 'P'.code.toByte()
        ) {
            return DetectedImageType("webp", "image/webp")
        }

        return null
    }
}

