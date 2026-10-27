package com.skillexchange.api.models.call

import kotlinx.serialization.Serializable

enum class CallStatus {
    DIALING,
    RINGING,
    CONNECTED,
    ENDED,
    REJECTED,
    BUSY,
    MISSED,
    FAILED;

    companion object {
        fun activeStatuses() = listOf(DIALING.name, RINGING.name, CONNECTED.name)
        fun terminalStatuses() = listOf(ENDED.name, REJECTED.name, BUSY.name, MISSED.name, FAILED.name)
    }
}

enum class CallType {
    VOICE,
    VIDEO;

    companion object {
        fun fromStringOrNull(raw: String): CallType? = try {
            valueOf(raw.trim().uppercase())
        } catch (_: Exception) {
            null
        }
    }
}

@Serializable
data class CallTokenRequest(
    val exchangeId: String,
    val callType: String = "VOICE"
)

@Serializable
data class CallTokenResponse(
    val url: String,
    val roomName: String,
    val token: String,
    val callType: String,
    val sessionId: String
)

@Serializable
data class CallActionRequest(
    val sessionId: String
)

@Serializable
data class CallEndRequest(
    val sessionId: String
)

@Serializable
data class CallEndResponse(
    val sessionId: String,
    val status: String,
    val durationSeconds: Int
)

@Serializable
data class CallSessionDto(
    val id: String,
    val exchangeId: String,
    val callerId: String,
    val receiverId: String,
    val roomName: String,
    val callType: String,
    val status: String,
    val startedAt: String,
    val connectedAt: String?,
    val endedAt: String?,
    val durationSeconds: Int
)
