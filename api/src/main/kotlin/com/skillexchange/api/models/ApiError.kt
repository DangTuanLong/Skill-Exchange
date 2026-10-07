package com.skillexchange.api.models

import kotlinx.serialization.Serializable

object ErrorCodes {
    const val VALIDATION_FAILED = "VALIDATION_FAILED"
    const val INVALID_TRANSITION = "INVALID_TRANSITION"
    const val ALREADY_RATED = "ALREADY_RATED"
    const val NOT_PARTICIPANT = "NOT_PARTICIPANT"
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val FORBIDDEN = "FORBIDDEN"
    const val NOT_FOUND = "NOT_FOUND"
    const val CONFLICT = "CONFLICT"
    const val PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE"
    const val UNSUPPORTED_MEDIA = "UNSUPPORTED_MEDIA"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val INTERNAL = "INTERNAL"
    const val BAD_REQUEST = "BAD_REQUEST"

    fun fromStatus(status: Int): String = when (status) {
        400 -> BAD_REQUEST
        401 -> UNAUTHORIZED
        403 -> FORBIDDEN
        404 -> NOT_FOUND
        409 -> CONFLICT
        413 -> PAYLOAD_TOO_LARGE
        415 -> UNSUPPORTED_MEDIA
        422 -> VALIDATION_FAILED
        429 -> RATE_LIMITED
        else -> INTERNAL
    }
}

@Serializable
data class ApiError(
    val success: Boolean = false,
    val status: Int,
    val code: String,
    val message: String,
    val fieldErrors: Map<String, String>? = null
)
