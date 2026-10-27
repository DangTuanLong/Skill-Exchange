package com.skillexchange.app.data.remote.call

import kotlinx.serialization.Serializable

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
    val connectedAt: String? = null,
    val endedAt: String? = null,
    val durationSeconds: Int = 0
)
