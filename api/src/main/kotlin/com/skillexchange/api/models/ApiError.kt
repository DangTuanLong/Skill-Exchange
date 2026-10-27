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
    const val QUERY_TIMEOUT = "QUERY_TIMEOUT"
    const val TEXT_TOO_LONG = "TEXT_TOO_LONG"
    const val INVALID_ENCODING = "INVALID_ENCODING"
    const val EMPTY_FILE = "EMPTY_FILE"
    const val UNSUPPORTED_FILE_TYPE = "UNSUPPORTED_FILE_TYPE"
    const val SERVER_BUSY = "SERVER_BUSY"
    const val PDF_ENCRYPTED = "PDF_ENCRYPTED"
    const val PDF_TOO_MANY_PAGES = "PDF_TOO_MANY_PAGES"
    const val PDF_NO_TEXT = "PDF_NO_TEXT"
    const val PDF_UNSAFE_CONTENT = "PDF_UNSAFE_CONTENT"
    const val EXCHANGE_NOT_ACTIVE = "EXCHANGE_NOT_ACTIVE"
    const val USER_BUSY = "USER_BUSY"
    const val CALL_COOLDOWN_ACTIVE = "CALL_COOLDOWN_ACTIVE"
    const val CALL_NOT_FOUND = "CALL_NOT_FOUND"
    const val LIVEKIT_NOT_CONFIGURED = "LIVEKIT_NOT_CONFIGURED"

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
